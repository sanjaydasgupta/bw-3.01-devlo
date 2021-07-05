package com.buildwhiz.utils

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

trait ImageUtils {

  def blankImage(h: Int, w: Int, format: String, color: Color = Color.lightGray): Array[Byte] = {
    val bufferedImage = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g2d = bufferedImage.createGraphics
    g2d.setColor(color)
    g2d.fillRect(0, 0, w, h)
    g2d.dispose()
    val byteArrayOutputStream = new ByteArrayOutputStream()
    ImageIO.write(bufferedImage, format, byteArrayOutputStream)
    byteArrayOutputStream.toByteArray
  }

}
