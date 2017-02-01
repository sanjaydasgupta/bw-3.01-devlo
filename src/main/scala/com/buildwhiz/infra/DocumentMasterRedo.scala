package com.buildwhiz.infra

import org.bson.Document
import org.bson.types.ObjectId
import BWMongoDB3._
import com.buildwhiz.utils.BWLogger

import scala.collection.JavaConverters._

object DocumentMasterRedo extends App {
  BWLogger.log(getClass.getName, "main()", "ENTRY")

  private val data =
    """{ "_id" : ObjectId("586b07d892982d0a9ccf5680"), "name" : "Sheet-1A", "timestamp" : NumberLong("1483409368688"), "description" : "Cover Sheet", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b082c92982d0a9ccf568d"), "name" : "Sheet-1B", "timestamp" : NumberLong("1483409452925"), "description" : "Site Plan", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1ac392982d0a9cb1ab4b"), "name" : "Sheet-2A", "timestamp" : NumberLong("1483414211834"), "description" : "Basement Floor Plan", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1b1592982d0a9cb1ab50"), "name" : "Sheet-3A", "timestamp" : NumberLong("1483414293213"), "description" : "Ground Floor Plan/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1b4192982d0a9cb1ab53"), "name" : "Sheet-3B", "timestamp" : NumberLong("1483414337348"), "description" : "2nd Floor Plan/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1b5892982d0a9cb1ab56"), "name" : "Sheet-3C", "timestamp" : NumberLong("1483414360758"), "description" : "3rd Floor Plan/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1f1a92982d0a9c3fe6a2"), "name" : "Sheet-3D", "timestamp" : NumberLong("1483415322688"), "description" : "Clerestory & Roof Plan/Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1f3f92982d0a9c3fe6a7"), "name" : "Sheet-3E", "timestamp" : NumberLong("1483415359220"), "description" : "Window & Door Schedule/Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1fac92982d0a9c3fe6ac"), "name" : "Sheet-3F", "timestamp" : NumberLong("1483415468023"), "description" : "Unit Electrical Plan-  Ground Floor/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1fca92982d0a9c3fe6b1"), "name" : "Sheet-3G", "timestamp" : NumberLong("1483415498815"), "description" : "Unit Electrical Plan-  2nd Floor/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b1ff992982d0a9c3fe6b6"), "name" : "Sheet-3H", "timestamp" : NumberLong("1483415545319"), "description" : "Unit Electrical Plan-  3rd Floor/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b203192982d0a9c3fe6bb"), "name" : "Sheet-3I", "timestamp" : NumberLong("1483415601270"), "description" : "Sections/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b204592982d0a9c3fe6c0"), "name" : "Sheet-3K", "timestamp" : NumberLong("1483415621786"), "description" : "Elevations/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b207b92982d0a9c3fe6c5"), "name" : "Sheet-4A", "timestamp" : NumberLong("1483415675164"), "description" : "Floor Plans/ Rear Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b209192982d0a9c3fe6c8"), "name" : "Sheet-4B", "timestamp" : NumberLong("1483415697093"), "description" : "Roof Plans/ Rear Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b20b992982d0a9c3fe6cb"), "name" : "Sheet-4C", "timestamp" : NumberLong("1483415737907"), "description" : "Window and Door Schedule/ Rear Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b20d692982d0a9c3fe6d0"), "name" : "Sheet-4D", "timestamp" : NumberLong("1483415766978"), "description" : "Electrical Plan/ Rear Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b20f292982d0a9c3fe6d5"), "name" : "Sheet-4E", "timestamp" : NumberLong("1483415794597"), "description" : "Sections/ Rear Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b210992982d0a9c3fe6da"), "name" : "Sheet-4G", "timestamp" : NumberLong("1483415817114"), "description" : "Elevations/ Rear Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b212c92982d0a9c3fe6df"), "name" : "Sheet-5A", "timestamp" : NumberLong("1483415852682"), "description" : "3D Perspectives", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b213d92982d0a9c3fe6e4"), "name" : "Sheet-5B", "timestamp" : NumberLong("1483415869312"), "description" : "3D Perspectives", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b215a92982d0a9c3fe6e9"), "name" : "Sheet-6A", "timestamp" : NumberLong("1483415898891"), "description" : "Details", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b216092982d0a9c3fe6ee"), "name" : "Sheet-6B", "timestamp" : NumberLong("1483415904726"), "description" : "Details", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b216792982d0a9c3fe6f3"), "name" : "Sheet-6C", "timestamp" : NumberLong("1483415911462"), "description" : "Details", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b216d92982d0a9c3fe6f8"), "name" : "Sheet-6D", "timestamp" : NumberLong("1483415917550"), "description" : "Details", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b21a592982d0a9c3fe6ff"), "name" : "Sheet-3J", "timestamp" : NumberLong("1483415973669"), "description" : "Sections/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586b21cd92982d0a9c3fe702"), "name" : "Sheet-3L", "timestamp" : NumberLong("1483416013693"), "description" : "Elevations/ Front Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |{ "_id" : ObjectId("586ba72d92982d0a52ca267f"), "name" : "Sheet-4F", "timestamp" : NumberLong("1483450157137"), "description" : "Sections/ Rear Building", "versions" : [ ], "project_id" : ObjectId("586336f692982d17cfd04bf8"), "content" : "PDF", "category" : "Architecture" }
      |""".stripMargin

  val documents: Seq[Document] = data.split("\n").map(d => Document.parse(d))

  val deleteResult = BWMongoDB3.document_master.deleteMany(Map("project_id" -> project430ForestOid))
  println(s"Deleted ${deleteResult.getDeletedCount} records")
  BWMongoDB3.document_master.insertMany(documents.asJava)
  val count = BWMongoDB3.document_master.count()
  println(s"Final count: $count records")

  BWLogger.log(getClass.getName, "main()", "EXIT")
}
