package com.stripe.ctf.instantcodesearch

import java.io._
import java.nio.file._

import com.twitter.concurrent.Broker

abstract class SearchResult
case class Match(path: String, line: Int) extends SearchResult
case class Done() extends SearchResult

class Searcher(indexer: Indexer)  {
  val index : Index = indexer.idx

  def search(needle : String, b : Broker[SearchResult]) = {
    
    for(fileEntry <- indexer.idx.map) {
      for (m <- tryFile(fileEntry._1, fileEntry._2, needle)) {
        b !! m
      }
    }

    b !! new Done()
  }

  def tryFile(file: String, text: String, needle: String) : Iterable[SearchResult] = {
    try {
      if (text.contains(needle)) {
        var line = 0
        return text.split("\n").zipWithIndex.
          filter { case (l,n) => l.contains(needle) }.
          map { case (l,n) => new Match(file, n+1) }
      }
    } catch {
      case e: IOException => {
        return Nil
      }
    }

    return Nil
  }
}
