# bobd-watermark
Tool to watermark images for big ocean, big data project

## Usage:

This app is a Java application. You'll need to have Java 8+ installed to use it. To run it and have it display help, use:

```bash
java -jar bobd-watermark-0.1.0-all.jar --help
```

This will display:

```
watermark 0.1
Usage: watermark [options] source target watermark

  source                   The directory of images to add watermarks too.
  target                   The directory to write the watermarked images into.
  watermark                The path to the images to use as an overlay
  -w, --width <value>      The overlay width as fraction of the image. Between 0 and 1. Default if 0.4
  -c, --copyright <value>  The copyright owner
  -r, --credit <value>     Image credit
  -o, --overwrite          If this flag is present, overwrite any existing images
  --help                   Add a watermark to every jpg and png image in a directory
```

Command line example:

```bash
java -jar bobd-watermark-0.1.0-all.jar \
    -c "Monterey Bay Aquarium Research Institute" \
    -o -w 0.1 \
    ~/images/raw ~/images/watermarked ~/images/overlay.png
```

## Build

This app is built with [Gradle](https://gradle.org/). Build it with:

```bash
gradle shadowJar
```

