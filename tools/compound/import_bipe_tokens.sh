#!/bin/bash

# Copyright 2026 Bipe.
#
# SPDX-License-Identifier: AGPL-3.0-only

# Bipe variant of import_tokens.sh: copies the açaí tokens from the local
# proseio-tokens clone (proseio/compound-design-tokens, private until launch)
# instead of cloning element-hq/compound-design-tokens.
# Usage: ./tools/compound/import_bipe_tokens.sh [path to proseio-tokens]

set -e

TOKENS="${1:-../proseio-tokens}"
if [ ! -d "$TOKENS/assets/android" ]; then
    echo "No assets/android in $TOKENS. Build proseio-tokens first (see its PROSEIO.md)." >&2
    exit 1
fi

echo "Copying files from $TOKENS..."
rm -R libraries/compound/src/main/res/drawable
cp -R "$TOKENS/assets/android/res/drawable" libraries/compound/src/main/res/
cp -R "$TOKENS"/assets/android/src/* libraries/compound/src/main/kotlin/io/element/android/compound/tokens/generated/
cp "$TOKENS/assets/android/res/theme.iife.js" libraries/compound/src/main/assets/theme.iife.js

echo "Adding autoMirrored attribute..."
python3 ./tools/compound/addAutoMirrored.py

echo "Done!"
