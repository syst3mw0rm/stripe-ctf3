package com.stripe.ctf.instantcodesearch

import com.twitter.util._
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.util.CharsetUtil.UTF_8
import scala.util.Random
import java.io._
import java.util.Arrays
import java.nio.file._
import java.nio.charset._
import java.nio.file.attribute.BasicFileAttributes
import com.google.gson._
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.handler.codec.http._

class SearchMasterServer(port: Int, id: Int) extends AbstractSearchServer(port, id) {
  val NumNodes = 3
  var indexRoot = "Not yet set"

  def this(port: Int) { this(port, 0) }

  val clients = (1 to NumNodes)
    .map { id => new SearchServerClient(port + id, id)}
    .toArray

  override def isIndexed() = {
    val responsesF = Future.collect(clients.map {client => client.isIndexed()})
    val successF = responsesF.map {responses => responses.forall { response =>

        (response.getStatus() == HttpResponseStatus.OK
          && response.getContent.toString(UTF_8).contains("true"))
      }
    }
    successF.map {success =>
      if (success) {
        successResponse()
      } else {
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "Nodes are not indexed")
      }
    }.rescue {
      case ex: Exception => Future.value(
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "Nodes are not indexed")
      )
    }
  }

  override def healthcheck() = {
    val responsesF = Future.collect(clients.map {client => client.healthcheck()})
    val successF = responsesF.map {responses => responses.forall { response =>
        response.getStatus() == HttpResponseStatus.OK
      }
    }
    successF.map {success =>
      if (success) {
        successResponse()
      } else {
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "All nodes are not up")
      }
    }.rescue {
      case ex: Exception => Future.value(
        errorResponse(HttpResponseStatus.BAD_GATEWAY, "All nodes are not up")
      )
    }
  }

  override def index(path: String) = {
    indexRoot = path
    
    System.err.println(
      "[master] Requesting " + NumNodes + " nodes to index path: " + path
    )
    System.err.println(
      "Randomly assigning each file to a server to index"
    )

    val allFilesToIndex = allFilePaths(path)
    
    val responses = Future.collect(allFilesToIndex.zipWithIndex.map {
        case (filePath, i) => {
          clients(i % clients.size).index(filePath)
        }
    })

    responses.map {_ => successResponse()}
  }



  override def query(q: String) = {
    val returnVal = Promise[HttpResponse]

    val responses = clients.map { client => client.query(q) }
    
    def getResult(res: HttpResponse): List[Match] = {
        val resString: String = res.getContent().toString(UTF_8)
        val jsonResult: JsonArray = new JsonParser().parse(resString).getAsJsonObject().get("results").getAsJsonArray();
        var m : List[Match] = List[Match]()
        for(i <- 0 to jsonResult.size() - 1) {
            val path = jsonResult.get(i).getAsString().split(":")(0)
            val line = Integer.parseInt(jsonResult.get(i).getAsString().split(":")(1))
            var relativePath = new File(indexRoot).toURI().relativize(new File(path).toURI()).getPath();
            m = m.::(new Match(relativePath, line))
        }
        return m
    }

    responses(0) onSuccess { a => 
      responses(1) onSuccess { b =>
        responses(2) onSuccess { c =>

          val results = getResult(a) ::: getResult(b) ::: getResult(c)
          returnVal.setValue(querySuccessResponse(results))
          //System.err.println(results)

        }
      }
    } onFailure {
      exc => 
        returnVal.setException(exc)
    }

    returnVal
  }


  def allFilePaths(path: String): List[String] = {
    var file_paths : List[String] = List[String]()
    val file: File = new File(path)
    val names : Array[String]  = file.list()

    for( name <- names) {
        file_paths = file_paths .:: (path + "/" + name)
    }

    return file_paths
  }

}
