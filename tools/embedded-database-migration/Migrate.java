// SPDX-License-Identifier: EPL-2.0
// Copies one system's MySQL schema into its embedded H2 database and verifies the copy (row counts and content digests).
// Reads MySQL only (one consistent read-only transaction). The target must not exist. Passwords come from files.
//   java -cp h2.jar:mariadb.jar Migrate.java <mysql-host> <schema> <root-password-file> <ca.crt> <schema.sql> <target-file> <user> <password-file>
import java.io.*;import java.nio.file.*;import java.security.*;import java.sql.*;import java.util.*;
public class Migrate {
  static String norm(Object o) throws Exception {
    if (o == null) return "\0";
    if (o instanceof Clob c) return c.getSubString(1, (int) c.length());
    if (o instanceof Blob b) return HexFormat.of().formatHex(b.getBytes(1, (int) b.length()));
    if (o instanceof byte[] b) return HexFormat.of().formatHex(b);
    if (o instanceof Boolean b) return b ? "1" : "0";
    if (o instanceof Number n) return new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
    return o.toString();
  }
  static Object read(ResultSet r, int i, int t) throws Exception {
    return switch (t) {
      case Types.TIMESTAMP -> r.getObject(i, java.time.LocalDateTime.class);
      case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> r.getBytes(i);
      case Types.CLOB, Types.LONGVARCHAR, Types.VARCHAR, Types.CHAR, Types.LONGNVARCHAR, Types.NVARCHAR -> r.getString(i);
      case Types.BOOLEAN, Types.BIT -> { boolean b = r.getBoolean(i); yield r.wasNull() ? null : b; }
      default -> r.getObject(i);
    };
  }
  // digest independent of row order; types are those of the target, so both sides are read alike
  static String digest(Connection c, String sql, int[] types) throws Exception {
    byte[] sum = new byte[32]; long n = 0;
    try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
      while (r.next()) { MessageDigest m = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < types.length; i++) { m.update(norm(read(r, i + 1, types[i])).getBytes("UTF-8")); m.update((byte) 1); }
        byte[] d = m.digest(); int carry = 0; for (int k = 31; k >= 0; k--) { int v = (sum[k] & 255) + (d[k] & 255) + carry; sum[k] = (byte) v; carry = v >> 8; } n++; } }
    return n + ":" + HexFormat.of().formatHex(sum, 0, 8);
  }
  public static void main(String[] a) throws Exception {
    if (Files.exists(Path.of(a[5] + ".mv.db"))) throw new IllegalStateException("target exists: " + a[5]);
    String my = "jdbc:mariadb://" + a[0] + ":3306/" + a[1] + "?sslMode=verify-full&serverSslCert=" + a[3] + "&tinyInt1isBit=false";
    String h2 = "jdbc:h2:file:" + a[5] + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;IGNORECASE=TRUE";
    long rows = 0; int tables = 0;
    try (Connection m = DriverManager.getConnection(my, "root", Files.readString(Path.of(a[2])).strip());
         Connection h = DriverManager.getConnection(h2, a[6], Files.readString(Path.of(a[7])).strip())) {
      m.createStatement().execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ"); m.createStatement().execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY");
      h.createStatement().execute("RUNSCRIPT FROM '" + a[4] + "'"); h.createStatement().execute("SET REFERENTIAL_INTEGRITY FALSE");
      Set<String> mine = new TreeSet<>(), theirs = new TreeSet<>();
      try (ResultSet r = h.createStatement().executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")) { while (r.next()) mine.add(r.getString(1)); }
      try (ResultSet r = m.createStatement().executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema='" + a[1] + "'")) { while (r.next()) theirs.add(r.getString(1)); }
      mine.remove("ah_seeded");
      if (!mine.equals(theirs)) throw new IllegalStateException("tables differ: embedded " + mine + " mysql " + theirs);
      h.setAutoCommit(false);
      for (String t : mine) {
        List<String> cols = new ArrayList<>(); List<Integer> ty = new ArrayList<>(); String id = null;
        try (ResultSet r = h.getMetaData().getColumns(null, "public", t, null)) { while (r.next()) { cols.add(r.getString("COLUMN_NAME")); ty.add(r.getInt("DATA_TYPE")); if ("YES".equals(r.getString("IS_AUTOINCREMENT"))) id = r.getString("COLUMN_NAME"); } }
        Set<String> mc = new TreeSet<>(); try (ResultSet r = m.createStatement().executeQuery("SELECT column_name FROM information_schema.columns WHERE table_schema='" + a[1] + "' AND table_name='" + t + "'")) { while (r.next()) mc.add(r.getString(1)); }
        if (!mc.equals(new TreeSet<>(cols))) throw new IllegalStateException(t + " columns differ: embedded " + cols + " mysql " + mc);
        String list = "`" + String.join("`,`", cols) + "`"; int[] types = ty.stream().mapToInt(i -> i).toArray();
        String q = "SELECT " + list + " FROM `" + t + "`";
        h.createStatement().execute("DELETE FROM `" + t + "`");
        long n = 0;
        try (Statement s = m.createStatement()) { s.setFetchSize(500);
          try (ResultSet r = s.executeQuery(q); PreparedStatement p = h.prepareStatement("INSERT INTO `" + t + "` (" + list + ") VALUES (" + "?,".repeat(cols.size()).replaceAll(",$", "") + ")")) {
            while (r.next()) { for (int i = 0; i < types.length; i++) p.setObject(i + 1, read(r, i + 1, types[i])); p.addBatch(); if (++n % 500 == 0) { p.executeBatch(); h.commit(); } }
            p.executeBatch(); h.commit(); } }
        if (id != null) { long next; try (ResultSet r = h.createStatement().executeQuery("SELECT COALESCE(MAX(`" + id + "`),0)+1 FROM `" + t + "`")) { r.next(); next = r.getLong(1); }
          try (ResultSet r = m.createStatement().executeQuery("SELECT COALESCE(auto_increment,1) FROM information_schema.tables WHERE table_schema='" + a[1] + "' AND table_name='" + t + "'")) { r.next(); next = Math.max(next, r.getLong(1)); }
          h.createStatement().execute("ALTER TABLE `" + t + "` ALTER COLUMN `" + id + "` RESTART WITH " + next); }
        String x = digest(m, q, types), y = digest(h, q, types);
        if (!x.equals(y)) throw new IllegalStateException(t + " differs: mysql " + x + " embedded " + y);
        rows += n; tables++;
      }
      h.createStatement().execute("SET REFERENTIAL_INTEGRITY TRUE"); h.commit();
    }
    System.out.println("OK " + a[1] + " tables=" + tables + " rows=" + rows + " (digests equal)");
  }
}
