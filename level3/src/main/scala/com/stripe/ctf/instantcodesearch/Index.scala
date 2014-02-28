package com.stripe.ctf.instantcodesearch

import java.io._

class Index() extends Serializable {
  val map = scala.collection.mutable.Map.empty[String, String]
  
  def addFile(file: String, text: String) {
    map(file) = text
  }

}

