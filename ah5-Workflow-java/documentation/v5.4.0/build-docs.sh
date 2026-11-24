#!/bin/sh
# Builds every document of the workflow documentation edition 5.4.0 and fails on undefined references or citations.
# Run python3 generator/generate.py first when the service contracts changed.
set -eu
cd "$(dirname "$0")"
status=0
for source in ./*.tex; do
  base=${source%.tex}
  if ! latexmk -pdf -interaction=nonstopmode -halt-on-error -quiet "$source" >/dev/null 2>&1; then
    echo "FAILED: $source (see $base.log)"; exit 1
  fi
  if grep -E "LaTeX Warning: (Reference|Citation) .* undefined|There were undefined references" "$base.log" >/dev/null; then
    echo "UNDEFINED REFERENCES: $source"; grep -E "undefined" "$base.log" | sort -u | head -5; status=1
  fi

  echo "built $base.pdf"
done
latexmk -c -quiet >/dev/null 2>&1
rm -f ./*.bbl
exit $status
