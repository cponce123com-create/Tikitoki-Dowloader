# Photo engine

The Android photo engine runs gallery-dl 1.32.21 as a separate program using the
Python runtime packaged by youtubedl-android. Its zipapp contains these pinned
pure-Python distributions: gallery-dl 1.32.21, requests 2.34.2, urllib3 2.8.0,
certifi 2026.11.20, charset-normalizer 3.5.3 and idna 3.20. Their license files
are included in the archive under their `.dist-info/licenses` directories.
The archive's executable entry point invokes `gallery_dl.main()` unmodified.

To rebuild the zipapp, install these exact versions into an empty pip target
with `--no-compile`, then run `python3 scripts/build_gallery_engine.py TARGET
app/src/main/assets/gallery-engine.pyz`. Update the SHA-256 constant in
`GalleryEngine.kt` whenever the archive changes. Keep upstream source and
license notices available with distributions.
