// Copyright (C) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License. See LICENSE in project root for information.

package com.microsoft.ml.spark

import java.net.URI

import org.apache.spark.sql.DataFrame
import com.microsoft.ml.spark.FileUtilities.File
import org.apache.spark.ml.linalg.DenseVector
import com.microsoft.ml.spark.Readers.implicits._
import org.apache.spark.ml.PipelineStage
import org.apache.spark.ml.util.{MLReadable, MLWritable}

import scala.collection.JavaConversions._

class ImageFeaturizerSuite extends LinuxOnly with CNTKTestUtils with RoundTripTestBase {
  val images: DataFrame = session.readImages(imagePath, true).withColumnRenamed("image", inputCol)

  val modelDir = new File(filesRoot, "CNTKModel")
  val modelDownloader = new ModelDownloader(session, modelDir.toURI)

  lazy val resNetUri: URI = new File(modelDir, "ResNet50_ImageNet.model").toURI
  lazy val resNet: ModelSchema = modelDownloader.downloadByName("ResNet50")

  test("Image featurizer should reproduce the CIFAR10 experiment") {
    val model = new ImageFeaturizer()
      .setInputCol(inputCol)
      .setOutputCol(outputCol)
      .setModelLocation(session, s"${sys.env("DATASETS_HOME")}/CNTKModel/ConvNet_CIFAR10.model")
      .setCutOutputLayers(0)
      .setLayerNames(Array("z"))
    val result = model.transform(images)
    compareToTestModel(result)
  }

  def resNetModel(): ImageFeaturizer = new ImageFeaturizer()
    .setInputCol(inputCol)
    .setOutputCol(outputCol)
    .setModel(session, resNet)

  test("the Image feature should work with the modelSchema") {
    val result = resNetModel().setCutOutputLayers(0).transform(images)
    compareToTestModel(result)
  }

  test("Image featurizer should work with ResNet50", TestBase.Extended) {
    val result = resNetModel().transform(images)
    val resVec = result.select(outputCol).collect()(0).getAs[DenseVector](0)
    assert(resVec.size == 1000)
  }

  test("test layers of network", TestBase.Extended) {
    (0 to 9).foreach({ i =>
      val model = new ImageFeaturizer()
        .setModel(session, resNet)
        .setInputCol(inputCol)
        .setOutputCol(outputCol)
        .setCutOutputLayers(i)
      val result = model.transform(images)
    })
  }
  val dfRoundTrip: DataFrame = images
  val reader: MLReadable[_] = ImageFeaturizer
  val modelReader: MLReadable[_] = ImageFeaturizer
  val stageRoundTrip: PipelineStage with MLWritable = resNetModel()

  test("an ImageFeaturizer should roundTrip") {
    testRoundTrip()
  }
}
