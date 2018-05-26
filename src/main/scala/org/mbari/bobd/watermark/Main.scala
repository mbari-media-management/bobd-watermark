package org.mbari.bobd.watermark


import java.awt.image.BufferedImage
import java.io.{BufferedOutputStream, FileOutputStream}
import java.nio.file.{Files, Path, Paths}
import java.text.SimpleDateFormat
import java.util.Date

import javax.imageio.ImageIO
import org.apache.commons.imaging.formats.tiff.constants.{ExifTagConstants, TiffTagConstants}
import scopt.{OptionParser, Read}

import scala.collection.JavaConverters._
import scala.util.control.NonFatal


/**
  *
  *
  * @author Brian Schlining
  * @since 2018-05-26T09:23:00
  */
object Main {

  val yearFormat = new SimpleDateFormat("yyyy")
  val now = new Date

  case class Config(source: Path = Paths.get(""),
                    target: Path = Paths.get(""),
                    watermark: String = "",
                    overlayPercentWidth: Double = 0.4,
                    copyright: Option[String] = None,
                    credits: Option[String] = None,
                    overwrite: Boolean = false)

  implicit val pathRead: Read[Path] = new Read[Path] {
    override def arity: Int = 1
    override def reads: String => Path = (s: String) => Paths.get(s)
  }

  val parser = new OptionParser[Config]("watermark") {
    head("watermark", "0.1")

    arg[Path]("source")
        .required()
        .action((x, c) =>  c.copy(source = x))
        .text("The directory of images to add watermarks too.")

    arg[Path](name = "target")
        .required()
        .action((x, c) => c.copy(target = x))
        .text("The directory to write the watermarked images into.")

    arg[String](name = "watermark")
        .required()
        .action((x, c) => c.copy(watermark = x))
        .text("The path to the images to use as an overlay")

    opt[Double]('w', name = "width")
        .action((x, c) => c.copy(overlayPercentWidth = x))
        .text("The overlay width as fraction of the image. Between 0 and 1. Default if 0.4")

    opt[String]('c', name = "copyright")
        .action((x, c) => c.copy(copyright = Some(x)))
        .text("The copyright owner")

    opt[String]('r', name = "credit")
            .action((x, c) => c.copy(credits = Some(x)))
            .text("Image credit")

    opt[Unit]('o', name = "overwrite")
            .action((_, c) => c.copy(overwrite = true))
            .text("If this flag is present, overwrite any existing images")

    help("help").text("Add a watermark to every jpg and png image in a directory")
  }

  def main(args: Array[String]): Unit = {

      parser.parse(args, Config()) match {
        case None => //Do nothign exit
        case Some(config) => run(config)
      }

  }

  def run(params: Config): Unit = {
    if (!Files.exists(params.source)) {
      println(s"WARNING: The directory ${params.source} does not exist")
    }
    else if (!Files.isDirectory(params.source)) {
      println(s"WARINING: ${params.source} is not a directory")
    }
    else {
      if (!Files.exists(params.target)) {
        Files.createDirectories(params.target)
      }

      val overlayPath = Paths.get(params.watermark)
      val overlay = ImageIO.read(overlayPath.toFile)

      val dirStream = Files.newDirectoryStream(params.source)
          .iterator()
          .asScala

      for (path <- dirStream) {
        val name = path.getFileName.toString.toLowerCase
        if (name.endsWith(".jpg") || name.endsWith(".png")) {
          val target = toTargetFile(path, params.target)
          if (!Files.exists(target) ||  params.overwrite) {
            println(s"Creating $target")
            try {
              watermark(path, overlay, params.overlayPercentWidth) match {
                case None => println("Failed to watermark " + path)
                case Some(image) =>
                  val bytes = if (path.getFileName.toString.toLowerCase.endsWith(".png")) {
                    addText(image, params.copyright.getOrElse(""), params.credits)
                  }
                  else {
                    addExif(image, params.copyright.getOrElse(""), params.credits)
                  }
                  val os = new BufferedOutputStream(new FileOutputStream(target.toFile))
                  os.write(bytes)
                  os.close()
              }
            }
            catch {
              case NonFatal(e) => println(s"Failed to write $target"); e.printStackTrace()
            }
          }
        }

      }

    }
  }

  def watermark(source: Path, overlay: BufferedImage, overlayPercentWidth: Double): Option[BufferedImage]  = {
    try {
      val image = ImageIO.read(source.toFile)
      Some(WatermarkUtilities.addWatermarkImage(image, overlay, overlayPercentWidth))
    }
    catch {
      case NonFatal(e) =>
        e.printStackTrace()
        None

    }
  }

  def addExif(image: BufferedImage,
              copyrightOwner: String,
              credit: Option[String]): Array[Byte] = {
    val jpegBytes = WatermarkUtilities.toJpegByteArray(image)
    val outputSet = WatermarkUtilities.getOrCreateOutputSet(jpegBytes)

    credit.foreach(s => {
      val exifDirectory = outputSet.getOrCreateExifDirectory()
      exifDirectory.removeField(ExifTagConstants.EXIF_TAG_USER_COMMENT)
      exifDirectory.add(ExifTagConstants.EXIF_TAG_USER_COMMENT, s)
    })

    val rootDirectory = outputSet.getOrCreateRootDirectory()

    rootDirectory.removeField(TiffTagConstants.TIFF_TAG_COPYRIGHT)
    rootDirectory.add(TiffTagConstants.TIFF_TAG_COPYRIGHT,
      s"Copyright ${yearFormat.format(now)} $copyrightOwner")

    WatermarkUtilities.addExifAsJPG(jpegBytes, outputSet)
  }

  /**
    * Adds iTXt metadata to a PNG image
    * @param image Java representation of the image
    * @return A PNG image as a byte array. Just write it out!!
    */
  def addText(image: BufferedImage,
              copyrightOwner: String,
              credit: Option[String]): Array[Byte] = {
    val text = Map(
      "Copyright" -> s"Copyright ${yearFormat.format(now)} $copyrightOwner") ++ credit.map("Source" -> _)
    WatermarkUtilities.addMetadataAsPNG(image, text)
  }

  def toTargetFile(srcFile: Path, targetPath: Path): Path =
    targetPath.resolve(srcFile.getFileName)

}
