package com.buildwhiz.tools.scripts

import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.BWMongoDB3._
import com.buildwhiz.infra.DynDoc._
import com.buildwhiz.infra.{BWMongoDB3, DynDoc}
import com.buildwhiz.utils.HttpUtils
import org.bson.types.ObjectId
import org.bson.Document
import com.mongodb.client.model.{InsertOneModel, UpdateOneModel}

import scala.jdk.CollectionConverters._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

object Omniclass33Add extends HttpUtils {

  val records: Seq[DynDoc] = Seq(
    Map("code" -> "33-11 00 00", "full_title" -> Seq("Planning Disciplines").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 11 00", "full_title" -> Seq("Planning Disciplines", "Regional Planning").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 21 00", "full_title" -> Seq("Planning Disciplines", "Development Planning").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 31 00", "full_title" -> Seq("Planning Disciplines", "Rural Planning").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 41 00", "full_title" -> Seq("Planning Disciplines", "Urban Planning").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 44 00", "full_title" -> Seq("Planning Disciplines", "Transportation Planning").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 51 00", "full_title" -> Seq("Planning Disciplines", "Environmental Planning").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 61 00", "full_title" -> Seq("Planning Disciplines", "Facility Conservation Planning").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 61 21", "full_title" -> Seq("Planning Disciplines", "Facility Conservation Planning", "Historic Building Conservation Planning").asJava, "parent_id" -> "33-11 61 00"),
    Map("code" -> "33-11 61 21 BW", "full_title" -> Seq("Planning Disciplines", "Facility Conservation Planning", "Historic Building Conservation Planning", "Historic-Consultant").asJava, "parent_id" -> "33-11 61 21"),
    Map("code" -> "33-11 61 31", "full_title" -> Seq("Planning Disciplines", "Facility Conservation Planning", "Ancient Monument Conservation Planning").asJava, "parent_id" -> "33-11 61 00"),
    Map("code" -> "33-11 61 41", "full_title" -> Seq("Planning Disciplines", "Facility Conservation Planning", "Archaeological Area Conservation Planning").asJava, "parent_id" -> "33-11 61 00"),
    Map("code" -> "33-11 BW 00", "full_title" -> Seq("Planning Disciplines", "BuildWhiz").asJava, "parent_id" -> "33-11 00 00"),
    Map("code" -> "33-11 BW 11", "full_title" -> Seq("Planning Disciplines", "BuildWhiz", "Current-Planning").asJava, "parent_id" -> "33-11 BW 00"),
    Map("code" -> "33-21 00 00", "full_title" -> Seq("Design Disciplines").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 11 00", "full_title" -> Seq("Design Disciplines", "Architecture").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 11 11", "full_title" -> Seq("Design Disciplines", "Architecture", "Residential Architecture").asJava, "parent_id" -> "33-21 11 00"),
    Map("code" -> "33-21 11 21", "full_title" -> Seq("Design Disciplines", "Architecture", "Commercial Architecture").asJava, "parent_id" -> "33-21 11 00"),
    Map("code" -> "33-21 11 24", "full_title" -> Seq("Design Disciplines", "Architecture", "Institutional Architecture").asJava, "parent_id" -> "33-21 11 00"),
    Map("code" -> "33-21 11 27", "full_title" -> Seq("Design Disciplines", "Architecture", "Industrial Architecture").asJava, "parent_id" -> "33-21 11 00"),
    Map("code" -> "33-21 21 00", "full_title" -> Seq("Design Disciplines", "Landscape Architecture").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 23 00", "full_title" -> Seq("Design Disciplines", "Interior Design").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 27 00", "full_title" -> Seq("Design Disciplines", "Graphic Design").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 27 11", "full_title" -> Seq("Design Disciplines", "Graphic Design", "Signage Graphic Design").asJava, "parent_id" -> "33-21 27 00"),
    Map("code" -> "33-21 25 00", "full_title" -> Seq("Design Disciplines", "Specifying").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 31 00", "full_title" -> Seq("Design Disciplines", "Engineering").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 31 BW", "full_title" -> Seq("Design Disciplines", "Engineering", "BuildWhiz").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 BW 11", "full_title" -> Seq("Design Disciplines", "Engineering", "BuildWhiz", "MEP").asJava, "parent_id" -> "33-21 31 BW"),
    Map("code" -> "33-21 31 02", "full_title" -> Seq("Design Disciplines", "Engineering", "Aerospace Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 04", "full_title" -> Seq("Design Disciplines", "Engineering", "Agricultural Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 06", "full_title" -> Seq("Design Disciplines", "Engineering", "Biomedical Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 08", "full_title" -> Seq("Design Disciplines", "Engineering", "Chemical Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 11", "full_title" -> Seq("Design Disciplines", "Engineering", "Civil Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 11 BW", "full_title" -> Seq("Design Disciplines", "Engineering", "Civil Engineering", "Soil-Consultant").asJava, "parent_id" -> "33-21 31 11"),
    Map("code" -> "33-21 31 11 11", "full_title" -> Seq("Design Disciplines", "Engineering", "Civil Engineering", "Geotechnical Engineering").asJava, "parent_id" -> "33-21 31 11"),
    Map("code" -> "33-21 31 14", "full_title" -> Seq("Design Disciplines", "Engineering", "Structural Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 14 11", "full_title" -> Seq("Design Disciplines", "Engineering", "Structural Engineering", "Foundation Engineering").asJava, "parent_id" -> "33-21 31 14"),
    Map("code" -> "33-21 31 14 21", "full_title" -> Seq("Design Disciplines", "Engineering", "Structural Engineering", "High-rise Engineering").asJava, "parent_id" -> "33-21 31 14"),
    Map("code" -> "33-21 31 14 31", "full_title" -> Seq("Design Disciplines", "Engineering", "Structural Engineering", "Long-span Structure Engineering").asJava, "parent_id" -> "33-21 31 14"),
    Map("code" -> "33-21 31 14 41", "full_title" -> Seq("Design Disciplines", "Engineering", "Structural Engineering", "Tensile Structure Engineering").asJava, "parent_id" -> "33-21 31 14"),
    Map("code" -> "33-21 31 14 51", "full_title" -> Seq("Design Disciplines", "Engineering", "Structural Engineering", "Pneumatic Structure Engineering").asJava, "parent_id" -> "33-21 31 14"),
    Map("code" -> "33-21 31 14 54", "full_title" -> Seq("Design Disciplines", "Engineering", "Structural Engineering", "Hydraulic Structure Engineering").asJava, "parent_id" -> "33-21 31 14"),
    Map("code" -> "33-21 31 17", "full_title" -> Seq("Design Disciplines", "Engineering", "Mechanical Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 17 11", "full_title" -> Seq("Design Disciplines", "Engineering", "Mechanical Engineering", "Plumbing Engineering").asJava, "parent_id" -> "33-21 31 17"),
    Map("code" -> "33-21 31 17 21", "full_title" -> Seq("Design Disciplines", "Engineering", "Mechanical Engineering", "Fire Protection Engineering").asJava, "parent_id" -> "33-21 31 17"),
    Map("code" -> "33-21 31 17 31", "full_title" -> Seq("Design Disciplines", "Engineering", "Mechanical Engineering", "Heating, Ventilation, and Air-Conditioning Engineering").asJava, "parent_id" -> "33-21 31 17"),
    Map("code" -> "33-21 31 17 33", "full_title" -> Seq("Design Disciplines", "Engineering", "Mechanical Engineering", "Refrigeration Engineering").asJava, "parent_id" -> "33-21 31 17"),
    Map("code" -> "33-21 31 17 34", "full_title" -> Seq("Design Disciplines", "Engineering", "Mechanical Engineering", "Energy Monitoring and Controls Engineering").asJava, "parent_id" -> "33-21 31 17"),
    Map("code" -> "33-21 31 17 37", "full_title" -> Seq("Design Disciplines", "Engineering", "Mechanical Engineering", "Hydrological Engineering").asJava, "parent_id" -> "33-21 31 17"),
    Map("code" -> "33-21 31 19", "full_title" -> Seq("Design Disciplines", "Engineering", "Mining and Geological Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 21", "full_title" -> Seq("Design Disciplines", "Engineering", "Electrical Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 21 11", "full_title" -> Seq("Design Disciplines", "Engineering", "Electrical Engineering", "High Voltage Electrical Engineering").asJava, "parent_id" -> "33-21 31 21"),
    Map("code" -> "33-21 31 21 21", "full_title" -> Seq("Design Disciplines", "Engineering", "Electrical Engineering", "Medium Voltage Electrical Engineering").asJava, "parent_id" -> "33-21 31 21"),
    Map("code" -> "33-21 31 21 31", "full_title" -> Seq("Design Disciplines", "Engineering", "Electrical Engineering", "Low Voltage Electrical Engineering").asJava, "parent_id" -> "33-21 31 21"),
    Map("code" -> "33-21 31 22", "full_title" -> Seq("Design Disciplines", "Engineering", "Electronics Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 23", "full_title" -> Seq("Design Disciplines", "Engineering", "Computer Hardware Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 24", "full_title" -> Seq("Design Disciplines", "Engineering", "Process Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 24 11", "full_title" -> Seq("Design Disciplines", "Engineering", "Process Engineering", "Piping Engineering").asJava, "parent_id" -> "33-21 31 24"),
    Map("code" -> "33-21 31 24 21", "full_title" -> Seq("Design Disciplines", "Engineering", "Process Engineering", "Wind Engineering").asJava, "parent_id" -> "33-21 31 24"),
    Map("code" -> "33-21 31 24 21 BW", "full_title" -> Seq("Design Disciplines", "Engineering", "Process Engineering", "Wind Engineering", "Wind-Consultant").asJava, "parent_id" -> "33-21 31 24 21"),
    Map("code" -> "33-21 31 24 31", "full_title" -> Seq("Design Disciplines", "Engineering", "Process Engineering", "Co-Generation Engineering").asJava, "parent_id" -> "33-21 31 24"),
    Map("code" -> "33-21 31 24 41", "full_title" -> Seq("Design Disciplines", "Engineering", "Process Engineering", "Nuclear Engineering").asJava, "parent_id" -> "33-21 31 24"),
    Map("code" -> "33-21 31 24 51", "full_title" -> Seq("Design Disciplines", "Engineering", "Process Engineering", "Sanitary Engineering").asJava, "parent_id" -> "33-21 31 24"),
    Map("code" -> "33-21 31 24 61", "full_title" -> Seq("Design Disciplines", "Engineering", "Process Engineering", "Petroleum Engineering").asJava, "parent_id" -> "33-21 31 24"),
    Map("code" -> "33-21 31 31", "full_title" -> Seq("Design Disciplines", "Engineering", "Environmental Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 33", "full_title" -> Seq("Design Disciplines", "Engineering", "Industrial Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 41", "full_title" -> Seq("Design Disciplines", "Engineering", "Marine Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 51", "full_title" -> Seq("Design Disciplines", "Engineering", "Materials Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 99", "full_title" -> Seq("Design Disciplines", "Engineering", "Specialty Engineering").asJava, "parent_id" -> "33-21 31 00"),
    Map("code" -> "33-21 31 99 11", "full_title" -> Seq("Design Disciplines", "Engineering", "Specialty Engineering", "Acoustical/Emanations Shielding Engineering").asJava, "parent_id" -> "33-21 31 99"),
    Map("code" -> "33-21 31 99 14", "full_title" -> Seq("Design Disciplines", "Engineering", "Specialty Engineering", "Antiterrorism/Physical Security Engineering").asJava, "parent_id" -> "33-21 31 99"),
    Map("code" -> "33-21 31 99 21", "full_title" -> Seq("Design Disciplines", "Engineering", "Specialty Engineering", "Communications Engineering").asJava, "parent_id" -> "33-21 31 99"),
    Map("code" -> "33-21 31 99 21 11", "full_title" -> Seq("Design Disciplines", "Engineering", "Specialty Engineering", "Communications Engineering", "Computer Network Engineering").asJava, "parent_id" -> "33-21 31 99 21"),
    Map("code" -> "33-21 31 99 21 21", "full_title" -> Seq("Design Disciplines", "Engineering", "Specialty Engineering", "Communications Engineering", "Alarm and Detection Engineering").asJava, "parent_id" -> "33-21 31 99 21"),
    Map("code" -> "33-21 31 99 21 31", "full_title" -> Seq("Design Disciplines", "Engineering", "Specialty Engineering", "Communications Engineering", "Audiovisual Engineering").asJava, "parent_id" -> "33-21 31 99 21"),
    Map("code" -> "33-21 31 99 31", "full_title" -> Seq("Design Disciplines", "Engineering", "Specialty Engineering", "Military Engineering").asJava, "parent_id" -> "33-21 31 99"),
    Map("code" -> "33-21 51 00", "full_title" -> Seq("Design Disciplines", "Design Support").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 51 11", "full_title" -> Seq("Design Disciplines", "Design Support", "Drafting").asJava, "parent_id" -> "33-21 51 00"),
    Map("code" -> "33-21 51 13", "full_title" -> Seq("Design Disciplines", "Design Support", "Model Making").asJava, "parent_id" -> "33-21 51 00"),
    Map("code" -> "33-21 51 16", "full_title" -> Seq("Design Disciplines", "Design Support", "Printing").asJava, "parent_id" -> "33-21 51 00"),
    Map("code" -> "33-21 51 19", "full_title" -> Seq("Design Disciplines", "Design Support", "Photography/Videography").asJava, "parent_id" -> "33-21 51 00"),
    Map("code" -> "33-21 51 19 11", "full_title" -> Seq("Design Disciplines", "Design Support", "Photography/Videography", "Photographic Services").asJava, "parent_id" -> "33-21 51 19"),
    Map("code" -> "33-21 51 19 13", "full_title" -> Seq("Design Disciplines", "Design Support", "Photography/Videography", "Commercial Photography").asJava, "parent_id" -> "33-21 51 19"),
    Map("code" -> "33-21 99 00", "full_title" -> Seq("Design Disciplines", "Specialty Design").asJava, "parent_id" -> "33-21 00 00"),
    Map("code" -> "33-21 99 10", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Building Envelope Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 11", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Fountain Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 14", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Finish Hardware Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 15", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Extraterrestrial Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 22", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Health Services Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 22 11", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Health Services Design", "Hospital Design").asJava, "parent_id" -> "33-21 99 22"),
    Map("code" -> "33-21 99 22 21", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Health Services Design", "Nursing Home Design").asJava, "parent_id" -> "33-21 99 22"),
    Map("code" -> "33-21 99 24", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Infrastructure Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 25", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Irrigation Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 26", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Laboratory Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 28", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Lighting Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 29", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Marina Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 31", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Environmental Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 31 11", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Environmental Design", "Tree Preservation").asJava, "parent_id" -> "33-21 99 31"),
    Map("code" -> "33-21 99 31 13", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Environmental Design", "Solar Design").asJava, "parent_id" -> "33-21 99 31"),
    Map("code" -> "33-21 99 31 BW", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Environmental Design", "Env-Consultant").asJava, "parent_id" -> "33-21 99 31"),
    Map("code" -> "33-21 99 45", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Transportation Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-21 99 45 11", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Transportation Design", "Air Transportation Design").asJava, "parent_id" -> "33-21 99 45"),
    Map("code" -> "33-21 99 45 21", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Transportation Design", "Roadway Transportation Design").asJava, "parent_id" -> "33-21 99 45"),
    Map("code" -> "33-21 99 45 31", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Transportation Design", "Marine Transportation Design").asJava, "parent_id" -> "33-21 99 45"),
    Map("code" -> "33-21 99 46", "full_title" -> Seq("Design Disciplines", "Specialty Design", "Vertical Conveyance Design").asJava, "parent_id" -> "33-21 99 00"),
    Map("code" -> "33-23 00 00", "full_title" -> Seq("Investigation Disciplines").asJava, "parent_id" -> "33-23 00 00"),
    Map("code" -> "33-23 11 00", "full_title" -> Seq("Investigation Disciplines", "Surveying").asJava, "parent_id" -> "33-23 00 00"),
    Map("code" -> "33-23 11 11", "full_title" -> Seq("Investigation Disciplines", "Surveying", "Cartography").asJava, "parent_id" -> "33-23 11 00"),
    Map("code" -> "33-23 11 21", "full_title" -> Seq("Investigation Disciplines", "Surveying", "Building Surveying").asJava, "parent_id" -> "33-23 11 00"),
    Map("code" -> "33-23 11 31", "full_title" -> Seq("Investigation Disciplines", "Surveying", "Site Surveying").asJava, "parent_id" -> "33-23 11 00"),
    Map("code" -> "33-23 21 00", "full_title" -> Seq("Investigation Disciplines", "Environmental Investigation").asJava, "parent_id" -> "33-23 00 00"),
    Map("code" -> "33-23 21 11", "full_title" -> Seq("Investigation Disciplines", "Environmental Investigation", "Environmental Impact Investigation").asJava, "parent_id" -> "33-23 21 00"),
    Map("code" -> "33-23 21 11 BW", "full_title" -> Seq("Investigation Disciplines", "Environmental Investigation", "Environmental Impact Investigation", "EIR-Consultant").asJava, "parent_id" -> "33-23 21 11"),
    Map("code" -> "33-23 21 21", "full_title" -> Seq("Investigation Disciplines", "Environmental Investigation", "Air Quality Evaluation Investigation").asJava, "parent_id" -> "33-23 21 00"),
    Map("code" -> "33-23 21 31", "full_title" -> Seq("Investigation Disciplines", "Environmental Investigation", "Hazardous Materials Investigation").asJava, "parent_id" -> "33-23 21 00"),
    Map("code" -> "33-23 31 00", "full_title" -> Seq("Investigation Disciplines", "Hydrological Investigation").asJava, "parent_id" -> "33-23 00 00"),
    Map("code" -> "33-23 41 00", "full_title" -> Seq("Investigation Disciplines", "Geotechnical Investigation").asJava, "parent_id" -> "33-23 00 00"),
    Map("code" -> "33-23 51 00", "full_title" -> Seq("Investigation Disciplines", "Risk Assessment").asJava, "parent_id" -> "33-23 00 00"),
    Map("code" -> "33-25 00 00", "full_title" -> Seq("Project Management Disciplines").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 11 00", "full_title" -> Seq("Project Management Disciplines", "Cost Estimation").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 11 11", "full_title" -> Seq("Project Management Disciplines", "Cost Estimation", "Value Engineering").asJava, "parent_id" -> "33-25 11 00"),
    Map("code" -> "33-25 14 00", "full_title" -> Seq("Project Management Disciplines", "Proposal Preparation").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 15 00", "full_title" -> Seq("Project Management Disciplines", "Architectural and Engineering Management").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 16 00", "full_title" -> Seq("Project Management Disciplines", "Construction Management").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 16 00 BW", "full_title" -> Seq("Project Management Disciplines", "Construction Management", "Project-Manager").asJava, "parent_id" -> "33-25 16 00"),
    Map("code" -> "33-25 16 11", "full_title" -> Seq("Project Management Disciplines", "Construction Management", "General Contracting").asJava, "parent_id" -> "33-25 16 00"),
    Map("code" -> "33-25 16 13", "full_title" -> Seq("Project Management Disciplines", "Construction Management", "Subcontracting").asJava, "parent_id" -> "33-25 16 00"),
    Map("code" -> "33-25 21 00", "full_title" -> Seq("Project Management Disciplines", "Scheduling").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 31 00", "full_title" -> Seq("Project Management Disciplines", "Contract Administration").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 41 00", "full_title" -> Seq("Project Management Disciplines", "Procurement Administration").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 41 14", "full_title" -> Seq("Project Management Disciplines", "Procurement Administration", "Product Sales").asJava, "parent_id" -> "33-25 41 00"),
    Map("code" -> "33-25 41 17", "full_title" -> Seq("Project Management Disciplines", "Procurement Administration", "Product Marketing").asJava, "parent_id" -> "33-25 41 00"),
    Map("code" -> "33-25 41 21", "full_title" -> Seq("Project Management Disciplines", "Procurement Administration", "Product Purchasing").asJava, "parent_id" -> "33-25 41 00"),
    Map("code" -> "33-25 51 00", "full_title" -> Seq("Project Management Disciplines", "Quality Assurance").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-25 51 11", "full_title" -> Seq("Project Management Disciplines", "Quality Assurance", "Construction Inspection").asJava, "parent_id" -> "33-25 51 00"),
    Map("code" -> "33-25 51 13", "full_title" -> Seq("Project Management Disciplines", "Quality Assurance", "Building Inspection").asJava, "parent_id" -> "33-25 51 00"),
    Map("code" -> "33-25 61 00", "full_title" -> Seq("Project Management Disciplines", "Property, Real Estate, and Community Association Management").asJava, "parent_id" -> "33-25 00 00"),
    Map("code" -> "33-41 00 00", "full_title" -> Seq("Construction Disciplines").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 01 00", "full_title" -> Seq("Construction Disciplines", "Material Moving Operations").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 01 11", "full_title" -> Seq("Construction Disciplines", "Material Moving Operations", "Conveyor Tending and Operations").asJava, "parent_id" -> "33-41 01 00"),
    Map("code" -> "33-41 01 13", "full_title" -> Seq("Construction Disciplines", "Material Moving Operations", "Crane and Tower Operations").asJava, "parent_id" -> "33-41 01 00"),
    Map("code" -> "33-41 01 14", "full_title" -> Seq("Construction Disciplines", "Material Moving Operations", "Hoist and Winch Operations").asJava, "parent_id" -> "33-41 01 00"),
    Map("code" -> "33-41 01 16", "full_title" -> Seq("Construction Disciplines", "Material Moving Operations", "Dredge, Excavating, and Loading Machine Operations").asJava, "parent_id" -> "33-41 01 00"),
    Map("code" -> "33-41 01 21", "full_title" -> Seq("Construction Disciplines", "Material Moving Operations", "Industrial Truck and Tractor Operations").asJava, "parent_id" -> "33-41 01 00"),
    Map("code" -> "33-41 01 31", "full_title" -> Seq("Construction Disciplines", "Material Moving Operations", "Laborers and Material Moving, Hand Operations").asJava, "parent_id" -> "33-41 01 00"),
    Map("code" -> "33-41 03 00", "full_title" -> Seq("Construction Disciplines", "Site Preparation").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 03 11", "full_title" -> Seq("Construction Disciplines", "Site Preparation", "Remediation Services").asJava, "parent_id" -> "33-41 03 00"),
    Map("code" -> "33-41 03 11 11", "full_title" -> Seq("Construction Disciplines", "Site Preparation", "Remediation Services", "Hazardous Material Abatement Services").asJava, "parent_id" -> "33-41 03 11"),
    Map("code" -> "33-41 03 21", "full_title" -> Seq("Construction Disciplines", "Site Preparation", "Demolition Services").asJava, "parent_id" -> "33-41 03 00"),
    Map("code" -> "33-41 03 31", "full_title" -> Seq("Construction Disciplines", "Site Preparation", "Fence Erection Services").asJava, "parent_id" -> "33-41 03 00"),
    Map("code" -> "33-41 03 41", "full_title" -> Seq("Construction Disciplines", "Site Preparation", "Foundation Preparation Services").asJava, "parent_id" -> "33-41 03 00"),
    Map("code" -> "33-41 03 41 11", "full_title" -> Seq("Construction Disciplines", "Site Preparation", "Foundation Preparation Services", "Hydrological Control Services").asJava, "parent_id" -> "33-41 03 41"),
    Map("code" -> "33-41 03 41 21", "full_title" -> Seq("Construction Disciplines", "Site Preparation", "Foundation Preparation Services", "Seismic Control Services").asJava, "parent_id" -> "33-41 03 41"),
    Map("code" -> "33-41 06 00", "full_title" -> Seq("Construction Disciplines", "Construction Labor, General").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 06 11", "full_title" -> Seq("Construction Disciplines", "Construction Labor, General", "Help, General Construction").asJava, "parent_id" -> "33-41 06 00"),
    Map("code" -> "33-41 06 13", "full_title" -> Seq("Construction Disciplines", "Construction Labor, General", "Construction Equipment Operation").asJava, "parent_id" -> "33-41 06 00"),
    Map("code" -> "33-41 09 00", "full_title" -> Seq("Construction Disciplines", "Supply Services").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 09 11", "full_title" -> Seq("Construction Disciplines", "Supply Services", "Fabrication Services").asJava, "parent_id" -> "33-41 09 00"),
    Map("code" -> "33-41 09 21", "full_title" -> Seq("Construction Disciplines", "Supply Services", "Manufacturing Services").asJava, "parent_id" -> "33-41 09 00"),
    Map("code" -> "33-41 10 00", "full_title" -> Seq("Construction Disciplines", "Carpentry").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 10 11", "full_title" -> Seq("Construction Disciplines", "Carpentry", "Rough Carpentry Contracting").asJava, "parent_id" -> "33-41 10 00"),
    Map("code" -> "33-41 10 21", "full_title" -> Seq("Construction Disciplines", "Carpentry", "Finished Carpentry Contracting").asJava, "parent_id" -> "33-41 10 00"),
    Map("code" -> "33-41 21 00", "full_title" -> Seq("Construction Disciplines", "Iron Working").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 21 11", "full_title" -> Seq("Construction Disciplines", "Iron Working", "Reinforcing Iron and Rebar Fabrication Services").asJava, "parent_id" -> "33-41 21 00"),
    Map("code" -> "33-41 23 00", "full_title" -> Seq("Construction Disciplines", "Boilermaker").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 24 00", "full_title" -> Seq("Construction Disciplines", "Sheet Metal Working").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 30 00", "full_title" -> Seq("Construction Disciplines", "Masonry Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 31 00", "full_title" -> Seq("Construction Disciplines", "Concrete Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 31 11", "full_title" -> Seq("Construction Disciplines", "Concrete Contracting", "Cement Finish Contracting").asJava, "parent_id" -> "33-41 31 00"),
    Map("code" -> "33-41 33 00", "full_title" -> Seq("Construction Disciplines", "Plaster Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 40 00", "full_title" -> Seq("Construction Disciplines", "Cladding Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 43 00", "full_title" -> Seq("Construction Disciplines", "Roofing Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 46 00", "full_title" -> Seq("Construction Disciplines", "Glazing Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 51 00", "full_title" -> Seq("Construction Disciplines", "Paneling Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 53 00", "full_title" -> Seq("Construction Disciplines", "Flooring Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 54 00", "full_title" -> Seq("Construction Disciplines", "Tile Setting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 56 00", "full_title" -> Seq("Construction Disciplines", "Painting Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 60 00", "full_title" -> Seq("Construction Disciplines", "Insulating Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 63 00", "full_title" -> Seq("Construction Disciplines", "Plumbing Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 64 00", "full_title" -> Seq("Construction Disciplines", "Waste Management Services").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 64 11", "full_title" -> Seq("Construction Disciplines", "Waste Management Services", "Waste Collection Services").asJava, "parent_id" -> "33-41 64 00"),
    Map("code" -> "33-41 64 21", "full_title" -> Seq("Construction Disciplines", "Waste Management Services", "Solid Waste Collection Services").asJava, "parent_id" -> "33-41 64 00"),
    Map("code" -> "33-41 64 31", "full_title" -> Seq("Construction Disciplines", "Waste Management Services", "Septic Tank Services and Sewer Pipe Cleaning").asJava, "parent_id" -> "33-41 64 00"),
    Map("code" -> "33-41 71 00", "full_title" -> Seq("Construction Disciplines", "Refrigeration Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 73 00", "full_title" -> Seq("Construction Disciplines", "Heating, Ventilation, and Air-Conditioning Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 76 00", "full_title" -> Seq("Construction Disciplines", "Electrical Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 76 11", "full_title" -> Seq("Construction Disciplines", "Electrical Contracting", "Low Voltage Contracting").asJava, "parent_id" -> "33-41 76 00"),
    Map("code" -> "33-41 76 21", "full_title" -> Seq("Construction Disciplines", "Electrical Contracting", "Medium Voltage Contracting").asJava, "parent_id" -> "33-41 76 00"),
    Map("code" -> "33-41 76 31", "full_title" -> Seq("Construction Disciplines", "Electrical Contracting", "High Voltage Contracting").asJava, "parent_id" -> "33-41 76 00"),
    Map("code" -> "33-41 79 00", "full_title" -> Seq("Construction Disciplines", "Control and Communication Services").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 81 00", "full_title" -> Seq("Construction Disciplines", "Environmental Energy Services").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 81 11", "full_title" -> Seq("Construction Disciplines", "Environmental Energy Services", "Geothermal Services").asJava, "parent_id" -> "33-41 81 00"),
    Map("code" -> "33-41 81 21", "full_title" -> Seq("Construction Disciplines", "Environmental Energy Services", "Solar Photovoltaic Services").asJava, "parent_id" -> "33-41 81 00"),
    Map("code" -> "33-41 81 31", "full_title" -> Seq("Construction Disciplines", "Environmental Energy Services", "Wind Turbine Services").asJava, "parent_id" -> "33-41 81 00"),
    Map("code" -> "33-41 83 00", "full_title" -> Seq("Construction Disciplines", "Fire Protection Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 86 00", "full_title" -> Seq("Construction Disciplines", "Conveyance Contracting").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 91 00", "full_title" -> Seq("Construction Disciplines", "Infrastructure Development").asJava, "parent_id" -> "33-41 00 00"),
    Map("code" -> "33-41 91 11", "full_title" -> Seq("Construction Disciplines", "Infrastructure Development", "Paving Contracting").asJava, "parent_id" -> "33-41 91 00"),
    Map("code" -> "33-41 91 21", "full_title" -> Seq("Construction Disciplines", "Infrastructure Development", "Rail-Track Laying Contracting").asJava, "parent_id" -> "33-41 91 00"),
    Map("code" -> "33-55 00 00", "full_title" -> Seq("Facility Use Disciplines").asJava, "parent_id" -> "33-55 00 00"),
    Map("code" -> "33-55 14 00", "full_title" -> Seq("Facility Use Disciplines", "Real Estate").asJava, "parent_id" -> "33-55 00 00"),
    Map("code" -> "33-55 14 11", "full_title" -> Seq("Facility Use Disciplines", "Real Estate", "Real Estate Sales").asJava, "parent_id" -> "33-55 14 00"),
    Map("code" -> "33-55 14 14", "full_title" -> Seq("Facility Use Disciplines", "Real Estate", "Property Appraising").asJava, "parent_id" -> "33-55 14 00"),
    Map("code" -> "33-55 14 17", "full_title" -> Seq("Facility Use Disciplines", "Real Estate", "Leasing Services").asJava, "parent_id" -> "33-55 14 00"),
    Map("code" -> "33-55 21 00", "full_title" -> Seq("Facility Use Disciplines", "Facility Owner").asJava, "parent_id" -> "33-55 00 00"),
    Map("code" -> "33-55 24 00", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations").asJava, "parent_id" -> "33-55 00 00"),
    Map("code" -> "33-55 24 11", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Space Planning").asJava, "parent_id" -> "33-55 24 00"),
    Map("code" -> "33-55 24 14", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Management").asJava, "parent_id" -> "33-55 24 00"),
    Map("code" -> "33-55 24 21", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance").asJava, "parent_id" -> "33-55 24 00"),
    Map("code" -> "33-55 24 21 03", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Operation and Maintenance Supervision").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 06", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "General Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 11", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Plumbing Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 14", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Fire Protection Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 17", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Heating, Ventilation, and Air-Conditioning Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 21", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Refrigeration Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 23", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Electrical Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 24", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Energy Monitoring and Controls Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 27", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Hydrological Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 31", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Lightning Protection Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 34", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Life Safety Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 37", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Radiation Protection Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 41", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Moisture Protection Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 44", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Indoor Air Quality Evaluation").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 47", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Communications Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 47 11", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Communications Operation and Maintenance", "Telecommunications Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21 47"),
    Map("code" -> "33-55 24 21 47 14", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Communications Operation and Maintenance", "Information Technology Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21 47"),
    Map("code" -> "33-55 24 21 51", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Facility Shielding Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 51 11", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Facility Shielding Operation and Maintenance", "Acoustic Shielding Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21 51"),
    Map("code" -> "33-55 24 21 61", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Industrial Machinery Operation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 21 71", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Operation and Maintenance", "Home Appliance Installation and Maintenance").asJava, "parent_id" -> "33-55 24 21"),
    Map("code" -> "33-55 24 23", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Support Services").asJava, "parent_id" -> "33-55 24 00"),
    Map("code" -> "33-55 24 23 11", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Support Services", "Building Cleaning").asJava, "parent_id" -> "33-55 24 23"),
    Map("code" -> "33-55 24 23 13", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Support Services", "Carpet and Upholstery Cleaning").asJava, "parent_id" -> "33-55 24 23"),
    Map("code" -> "33-55 24 23 21", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Support Services", "Grounds Maintenance Workers").asJava, "parent_id" -> "33-55 24 23"),
    Map("code" -> "33-55 24 23 31", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Support Services", "Pest Control").asJava, "parent_id" -> "33-55 24 23"),
    Map("code" -> "33-55 24 23 41", "full_title" -> Seq("Facility Use Disciplines", "Facility Operations", "Facility Support Services", "Facility Security").asJava, "parent_id" -> "33-55 24 23"),
    Map("code" -> "33-55 36 00", "full_title" -> Seq("Facility Use Disciplines", "Facility Restoration Services").asJava, "parent_id" -> "33-55 00 00"),
    Map("code" -> "33-55 36 11", "full_title" -> Seq("Facility Use Disciplines", "Facility Restoration Services", "Concrete Restoration Services").asJava, "parent_id" -> "33-55 36 00"),
    Map("code" -> "33-55 36 21", "full_title" -> Seq("Facility Use Disciplines", "Facility Restoration Services", "Masonry Restoration Services").asJava, "parent_id" -> "33-55 36 00"),
    Map("code" -> "33-55 36 31", "full_title" -> Seq("Facility Use Disciplines", "Facility Restoration Services", "Parking Restoration Services").asJava, "parent_id" -> "33-55 36 00"),
    Map("code" -> "33-81 00 00", "full_title" -> Seq("Support Disciplines").asJava, "parent_id" -> "33-81 00 00"),
    Map("code" -> "33-81 11 00", "full_title" -> Seq("Support Disciplines", "Legal Services").asJava, "parent_id" -> "33-81 00 00"),
    Map("code" -> "33-81 11 11", "full_title" -> Seq("Support Disciplines", "Legal Services", "Codes Consultation").asJava, "parent_id" -> "33-81 11 00"),
    Map("code" -> "33-81 11 14", "full_title" -> Seq("Support Disciplines", "Legal Services", "Forensic Investigation").asJava, "parent_id" -> "33-81 11 00"),
    Map("code" -> "33-81 11 17", "full_title" -> Seq("Support Disciplines", "Legal Services", "Permitting").asJava, "parent_id" -> "33-81 11 00"),
    Map("code" -> "33-81 11 21", "full_title" -> Seq("Support Disciplines", "Legal Services", "Lawyer").asJava, "parent_id" -> "33-81 11 00"),
    Map("code" -> "33-81 11 BW", "full_title" -> Seq("Support Disciplines", "Legal Services", "Land-Use-Attorney").asJava, "parent_id" -> "33-81 11 00"),
    Map("code" -> "33-81 11 23", "full_title" -> Seq("Support Disciplines", "Legal Services", "Notary").asJava, "parent_id" -> "33-81 11 00"),
    Map("code" -> "33-81 21 00", "full_title" -> Seq("Support Disciplines", "Administrative and General Consulting").asJava, "parent_id" -> "33-81 00 00"),
    Map("code" -> "33-81 21 11", "full_title" -> Seq("Support Disciplines", "Administrative and General Consulting", "Public Relations").asJava, "parent_id" -> "33-81 21 00"),
    Map("code" -> "33-81 21 11 11", "full_title" -> Seq("Support Disciplines", "Administrative and General Consulting", "Public Relations", "Translation and Interpretation Services").asJava, "parent_id" -> "33-81 21 11"),
    Map("code" -> "33-81 21 21", "full_title" -> Seq("Support Disciplines", "Administrative and General Consulting", "Operations Management").asJava, "parent_id" -> "33-81 21 00"),
    Map("code" -> "33-81 21 21 11", "full_title" -> Seq("Support Disciplines", "Administrative and General Consulting", "Operations Management", "Administrative Services Management").asJava, "parent_id" -> "33-81 21 21"),
    Map("code" -> "33-81 21 21 13", "full_title" -> Seq("Support Disciplines", "Administrative and General Consulting", "Operations Management", "Computer and Information Systems Management").asJava, "parent_id" -> "33-81 21 21"),
    Map("code" -> "33-81 31 00", "full_title" -> Seq("Support Disciplines", "Finance").asJava, "parent_id" -> "33-81 00 00"),
    Map("code" -> "33-81 31 11", "full_title" -> Seq("Support Disciplines", "Finance", "Banking").asJava, "parent_id" -> "33-81 31 00"),
    Map("code" -> "33-81 31 14", "full_title" -> Seq("Support Disciplines", "Finance", "Accounting").asJava, "parent_id" -> "33-81 31 00"),
    Map("code" -> "33-81 31 17", "full_title" -> Seq("Support Disciplines", "Finance", "Insurance").asJava, "parent_id" -> "33-81 31 00"),
    Map("code" -> "33-81 31 19", "full_title" -> Seq("Support Disciplines", "Finance", "Purchasing Management").asJava, "parent_id" -> "33-81 31 00"),
    Map("code" -> "33-81 31 21", "full_title" -> Seq("Support Disciplines", "Finance", "Bonding").asJava, "parent_id" -> "33-81 31 00"),
    Map("code" -> "33-81 31 23", "full_title" -> Seq("Support Disciplines", "Finance", "Compensation and Benefits Management").asJava, "parent_id" -> "33-81 31 00"),
    Map("code" -> "33-81 31 26", "full_title" -> Seq("Support Disciplines", "Finance", "Human Resources Management").asJava, "parent_id" -> "33-81 31 00"),
    Map("code" -> "33-BW 00 00", "full_title" -> Seq("Other").asJava, "parent_id" -> "33-BW 00 00")
  )

  def main(request: HttpServletRequest, response: HttpServletResponse, args: Array[String]): Unit = {
    val writer = response.getWriter
    def output(s: String): Unit = writer.print(s)
    response.setContentType("text/html")
    output(s"<html><body>")

    output(s"${getClass.getName}:main() ENTRY<br/>")
    val user: DynDoc = getUser(request)
    if (!PersonApi.isBuildWhizAdmin(Right(user)) || user.first_name[String] != "Sanjay") {
      throw new IllegalArgumentException("Not permitted")
    }
    val existingCount = BWMongoDB3.omni33classes.countDocuments()
    if (existingCount > 0) {
      output(s"ALREADY EXISTS: $existingCount Omniclass33 records - will QUIT<br/>")
    } else {
      output(s"Omniclass33 Record count: ${records.length}<br/>")
      if (args.length >= 1 && args(0) == "GO") {
        val bulkInsertBuffer = records.map(r => {
          val d = r.asDoc.append("__v", 0)
          new InsertOneModel(d)
        })
        output(s"Inserting ${bulkInsertBuffer.length} records into collection 'omni33classes'<br/>")
        val bulkWriteResult = BWMongoDB3.omni33classes.bulkWrite(bulkInsertBuffer.asJava)
        if (bulkWriteResult.getInsertedCount != bulkInsertBuffer.length) {
          output(s"""<font color="red">ERROR during bulkWrite(insert): $bulkWriteResult<font/><br/>""")
        } else {
          output(s"Inserted ${bulkWriteResult.getInsertedCount} records<br/>")
        }
        val code2id: Map[String, ObjectId] = records.map(r => (r.code[String], r._id[ObjectId])).toMap
        val bulkUpdateBuffer = records.map(r => {
          new UpdateOneModel[Document](Map("_id" -> r._id[ObjectId]),
              Map($set -> Map("parent_id" -> code2id(r.parent_id[String]))))
        })
        val bulkUpdateResult = BWMongoDB3.omni33classes.bulkWrite(bulkUpdateBuffer.asJava)
        if (bulkUpdateResult.getModifiedCount != bulkUpdateBuffer.length) {
          output(s"""<font color="red">ERROR during bulkWrite(update): $bulkUpdateResult<font/><br/>""")
        } else {
          output(s"Updated ${bulkUpdateResult.getInsertedCount} records<br/>")
        }
        val projects: Seq[DynDoc] = BWMongoDB3.projects.find()
        val bulkInsertBuffer2 = projects.map(project => {
          val newRecord = new Document("project_id", project._id[ObjectId]).append("codes", Seq(
            "33-11 00 00", "33-11 21 00", "33-11 51 00", "33-11 61 21", "33-21 00 00", "33-21 11 00", "33-21 11 11",
            "33-21 11 21", "33-21 21 00", "33-21 23 00", "33-21 27 00", "33-21 31 00", "33-21 31 BW", "33-21 31 11",
            "33-21 31 11 BW", "33-21 31 11 11", "33-21 31 14", "33-21 31 17", "33-21 31 17 11", "33-21 31 17 21",
            "33-21 31 17 34", "33-21 31 21", "33-21 31 21 31", "33-21 31 24 21", "33-21 31 99 11",
            "33-21 31 99 21 11", "33-21 31 99 21 21", "33-21 31 99 21 31", "33-21 51 00", "33-21 51 11",
            "33-21 51 16", "33-21 51 19", "33-21 99 10", "33-21 99 25", "33-21 99 28", "33-21 99 31 11",
            "33-21 99 31 13", "33-21 99 46", "33-23 00 00", "33-23 11 00", "33-23 21 00", "33-23 21 11",
            "33-23 21 21", "33-23 21 31", "33-23 41 00", "33-23 51 00", "33-25 00 00", "33-25 11 00", "33-25 11 11",
            "33-25 14 00", "33-25 15 00", "33-25 16 00", "33-25 16 11", "33-25 16 13", "33-25 21 00", "33-25 31 00",
            "33-25 41 00", "33-25 41 21", "33-25 51 00", "33-25 51 11", "33-25 51 13", "33-25 61 00", "33-41 00 00",
            "33-41 01 00", "33-41 01 13", "33-41 01 14", "33-41 01 16", "33-41 01 31", "33-41 03 00",
            "33-41 03 11 11", "33-41 03 21", "33-41 03 31", "33-41 06 00", "33-41 06 11", "33-41 09 00",
            "33-41 09 11", "33-41 10 00", "33-41 10 11", "33-41 10 21", "33-41 21 00", "33-41 21 11", "33-41 24 00",
            "33-41 30 00", "33-41 31 00", "33-41 31 11", "33-41 33 00", "33-41 40 00", "33-41 43 00", "33-41 46 00",
            "33-41 51 00", "33-41 53 00", "33-41 54 00", "33-41 56 00", "33-41 60 00", "33-41 63 00", "33-41 64 00",
            "33-41 64 11", "33-41 64 31", "33-41 73 00", "33-41 76 00", "33-41 76 11", "33-41 79 00", "33-41 81 21",
            "33-41 83 00", "33-41 91 11", "33-55 00 00", "33-55 14 14", "33-55 14 17", "33-55 21 00", "33-55 24 00",
            "33-55 24 14", "33-55 24 21", "33-55 24 23 41", "33-81 00 00", "33-81 11 11", "33-81 11 17",
            "33-81 11 21", "33-81 11 BW", "33-81 11 23", "33-81 21 11", "33-81 21 21 11", "33-81 21 21 13",
            "33-81 31 00", "33-81 31 11", "33-81 31 14", "33-81 31 17", "33-81 31 19", "33-81 31 26").asJava)
          new InsertOneModel(newRecord)
        })
        output(s"Inserting ${bulkInsertBuffer2.length} records into collection 'project_omni33classes'<br/>")
        val bulkWriteResult2 = BWMongoDB3.project_omni33classes.bulkWrite(bulkInsertBuffer2.asJava)
        if (bulkWriteResult2.getInsertedCount != bulkInsertBuffer2.length) {
          output(s"""<font color="red">ERROR during bulkWrite(insert): $bulkWriteResult2<font/><br/>""")
        } else {
          output(s"Inserted ${bulkWriteResult2.getInsertedCount} records<br/>")
        }
      }
    }
    output(s"${getClass.getName}:main() EXIT-OK<br/>")
    output("</body></html>")
  }

}
