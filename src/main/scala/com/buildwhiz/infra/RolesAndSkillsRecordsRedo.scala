package com.buildwhiz.infra

import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConverters._

object RolesAndSkillsRecordsRedo extends App {

  private val skills = Seq(
    "Arboriculture", "Architecture", "Civil", "Electrical", "Energy", "Landscaping", "Mechanical",
    "Plumbing", "Structural"
  ).map(skill => new Document("_id", skill))

  private val roles = Seq(
    Seq("585b3c3c92982d14ba5a229c", "Admin", "Business"),
    Seq("585b3c3c92982d14ba5a229d", "Admin", "Database"),
    Seq("585b3c3c92982d14ba5a229e", "Admin", "Admin"),
    Seq("585a699592982d1085de5f57", "Design", "Arborist"),
    Seq("585a699592982d1085de5f56", "Design", "Architect"),
    Seq("585a699592982d1085de5f55", "Design", "Building Scientist"),
    Seq("585a699592982d1085de5f54", "Design", "Civil Engineer"),
    Seq("585a699592982d1085de5f53", "Design", "Electrical Engineer"),
    Seq("585a699592982d1085de5f52", "Design", "Energy Conservation"),
    Seq("585a699592982d1085de5f51", "Design", "Geotechnical Engineer"),
    Seq("585a699592982d1085de5f50", "Design", "Interiors Designer"),
    Seq("585a699592982d1085de5f4f", "Design", "Landscape Designer"),
    Seq("585a699592982d1085de5f4e", "Design", "Mechanical Engineer"),
    Seq("585a699592982d1085de5f4d", "Design", "Plumbing Engineer"),
    Seq("585a699592982d1085de5f4c", "Design", "Structural Engineer"),
    Seq("585a699592982d1085de5f4b", "Design", "Surveyor"),
    Seq("585a699592982d1085de5f4a", "Construction", "Civil Contractor"),
    Seq("585a699592982d1085de5f49", "Construction", "Concrete"),
    Seq("585a699592982d1085de5f48", "Construction", "Drywall"),
    Seq("585a699592982d1085de5f47", "Construction", "Electrician"),
    Seq("585a699592982d1085de5f46", "Construction", "Foundation"),
    Seq("585a699592982d1085de5f45", "Construction", "Framer"),
    Seq("585a699592982d1085de5f44", "Construction", "Mechanic"),
    Seq("585a699592982d1085de5f43", "Construction", "Plumber"),
    Seq("585a699592982d1085de5f42", "Construction", "Roofer"),
    Seq("585a699592982d1085de5f41", "Construction", "Siding"),
    Seq("585a699592982d1085de5f40", "Construction", "Stucco"),
    Seq("585a699592982d1085de5f3f", "Management", "Owner"),
    Seq("585a699592982d1085de5f3e", "Management", "Project Manager"),
    Seq("585a699592982d1085de5f3d", "Management", "Site Manager")
  ).map(row => Map("_id" -> new ObjectId(row.head), "category" -> row(1), "name" -> row(2))).
    map(map => new Document(map.asJava.asInstanceOf[java.util.Map[String, Object]]))

  println(s"Original roles count: ${BWMongoDB3.roles_master.count()}")
  BWMongoDB3.roles_master.insertMany(roles.asJava)
  println(s"Final roles count: ${BWMongoDB3.roles_master.count()}")

  println(s"Original skills count: ${BWMongoDB3.skills_master.count()}")
  BWMongoDB3.skills_master.insertMany(skills.asJava)
  println(s"Final skills count: ${BWMongoDB3.skills_master.count()}")

}
