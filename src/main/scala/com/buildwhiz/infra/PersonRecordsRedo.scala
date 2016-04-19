package com.buildwhiz.infra

import java.security.MessageDigest

import com.buildwhiz.Utils
import com.buildwhiz.infra.BWMongoDB3._
import org.bson.Document
import org.bson.types.ObjectId

import scala.collection.JavaConversions._
import scala.collection.mutable

object PersonRecordsRedo extends App with Utils {
  BWLogger.log(getClass.getName, "main()", "ENTRY")

  private val data =
    """id	first_name	middle_name	last_name	notes	emails.work	emails.other	phones.home	phones.work	phones.fax	phones.mobile	address.formatted	address.street	address.city	address.state	address.zip	company	title	role
      |56f1241ed5d8ad2539b1e070	Brian		von Allworden		brianv@wrightengineers.com			+14804836111		+1 602-505-1218	7400 W. Detroit Street, Suite 170  Chandler, AZ 85226	7400 W. Detroit Street, Suite 170	Chandler	AZ	85226	Wright Engineers	Structural Engineer	33-21 31 14
      |56f1241ed5d8ad2539b1e071	Caroline		Chen		chiiluh@yahoo.com					(650) 996-0622	212 high street  pato alto ca 9430	212 high street  pato alto ca 9430				David Solnick Architect	Architect	33-21 31 14 01
      |56f1241ed5d8ad2539b1e072	Charles	M.	Salter		charles.salter@cmsalter.com			415.470.5422	415.397.0454		130 Sutter Street, Floor 5, San Francisco, CA, 94104	130 Sutter Street, Floor 5	San Francisco	CA	94104	Charles M. Salter Associates, Inc.	President	33-21 31 99 11
      |56f1241ed5d8ad2539b1e073	Colin		Shane		cshane@rdh.com			510 788 8916		415 793 7780	360 22nd Street, #710,  Oakland, CA 94612	360 22nd Street, #710	Oakland	CA	94612	RDH Building Science Inc.	Associate, Senior Project Manager	33-21 99 10
      |56f1241ed5d8ad2539b1e074	Dan		Dyckman		dan.geoforensics@yahoo.com			650-349-3369		415-370-8355	561 Pilgrim Dr, Foster City, CA 94404	561 Pilgrim Dr	Foster City	CA	94404	Geoforensics	???	33-21 31 11 11
      |56f1241ed5d8ad2539b1e075	Dan		MacLeod		dmacleod@macleodassociates.net			650-593-8580  Ext. 101	650-593-8675		965 Center Street San Carlos, CA 94070	965 Center Street	San Carlos	CA	94070	MacLeod & Associates, Inc.	???	33-23 11 00
      |56f1241ed5d8ad2539b1e076	David		Solnick		david@solnick.net					1 (650) 328-8065	350 W 42 Street #31C  New York, NY 10036 ::: 212 high street  pato alto ca 94301	350 W 42 Street #31C ::: 212 high street  pato alto	New York :::	NY ::: ca	10036 ::: 94301	David Solnick Architect	owner	33-21 11 10
      |56f1241ed5d8ad2539b1e077	Des		Nolan		des@hardrockconcrete1.com			408-481-4990	408-481-4993	408-390-2724	241 Commercial Street, Sunnyvale CA. 94085	241 Commercial Street	Sunnyvale	CA	94085	Hardrock Concrete	???	33-41 31 00
      |56f1241ed5d8ad2539b1e078	Dipak		Roy		dipak.Roy@fremontbank.com	dipakroy@sbcglobal.net		1-510-505-5239		1-510-928-2061	39150 Fremont Blvd.,  Fremont, CA 94538	39150 Fremont Blvd.	Fremont	CA	94538	Fremont Bank	Vice-President, Commercial Banking
      |56f1241ed5d8ad2539b1e079	Dusan		Sindjic		dusan@acies.net			(408) 522-5255 x143			3371 Olcott Street Santa Clara, CA 95054	3371 Olcott Street	Santa Clara	CA	95054	ACIES ENGINEERING	PROJECT DESIGNER	33-21 31 17 31
      |56f12485d5d8ad257a7a8291	Fred		Reynolds		fred.brady.reynolds@gmail.com		8316620833			4153856239	215 Wixon ave. Aptos CA 95003	215 Wixon ave.	Aptos	CA	95003	Reynolds Construction	???	33-25 16 00
      |56f12485d5d8ad257a7a8292	Gary		Hsu		hhhsu@sbcglobal.net			510-668-1815	510-490-8690		PO Box 14198, Fremont, CA 94538 United States of America	PO Box 14198,	Fremont	CA	94538	Capex Engineering Inc.	???	33-25 51 11
      |56f12485d5d8ad257a7a8293	Joey		Trott		joeyt@touchatt.com			(650) 322-1256		(650) 537-2471	2535 Pulgas Ave. East Palo Alto, CA 94303	2535 Pulgas Ave.	East Palo Alto	CA	94303	Toubar Equipment company Inc.	???	33-41 01 16
      |56f12485d5d8ad257a7a8294	John		Cinti		john@jcintidesigns.com			203-307-0737		+16507409364	304 HOMELAND ST. FAIRFIELD, CT 06825	304 HOMELAND ST.	FAIRFIELD	CT	6825	JOHN CINTI DESIGNS, LLC	???	33-21 23 00
      |56f12485d5d8ad257a7a8295	Linn		Winterbotham	CLA #1743	winterbotham@jps.net			650.325.3137		650.823.0291	727 Paradise Way, Emerald Hills, CA 94062	727 Paradise Way	Emerald Hills	CA	94062	???	???	33-21 21 00
      |56f12485d5d8ad257a7a8296	Nazar		Mishchuk		nazar@acies.net			(408) 522-5255 x 108	(408) 522-5260		3371 Olcott Street Santa Clara, CA 95054	3371 Olcott Street	Santa Clara	CA	95054	ACIES ENGINEERING	Plumbing Project Manager	33-21 31 17 11
      |56f12485d5d8ad257a7a8297	Ray		Williams	Lic. # 705169	rawilliamsent@gmail.com			408­998­2245	408­298­1985		1584 BRANHAM LANE #201 SAN JOSE, CA 95118	1584 BRANHAM LANE #201	SAN JOSE	CA	95118	United Fire Safety	???	33-41 83 00
      |56f12485d5d8ad257a7a8298	Sal	P.	Italiano		Sal Italiano sal@spi-consulting.com			925 299 1341		510 697 7109	971 Dewing Avenue, Suite 201 Lafayette, CA 94549	971 Dewing Avenue, Suite 201	Lafayette	CA	94549	SPI Consulting Engineers, Inc	???	33-21 31 14
      |56f12485d5d8ad257a7a8299	Scott		Rehn		scottdemo@aol.com			(650)593-7799	(650)369-4315		P.O. Box 6309 San Mateo, Ca 94403	P.O. Box 6309	San Mateo	Ca	94403	Scott's Demolition	???	33-41 03 21
      |56f12485d5d8ad257a7a829a	Tomislav		Gajic		tomislav@acies.net			(408) 522-5255 x104	(408) 522-5260		3371 Olcott Street Santa Clara, CA 95054	3371 Olcott Street	Santa Clara	CA	95054	ACIES ENGINEERING	Principal	33-21 31 21
      |56f124dfd5d8ad25b1325b39	Vergel		Galura		vgalura@macleodassociates.net			(650) 593-8580 ext. 102			965 Center Street San Carlos, CA 94070	965 Center Street	San Carlos	CA	94070	MacLeod & Associates, Inc.	Civil Engineer	33-21 31 11
      |56f124dfd5d8ad25b1325b3d	Victor		Melean		victor@acies.net			(408) 522-5255 x165			3371 Olcott Street Santa Clara, CA 95054	3371 Olcott Street	Santa Clara	CA	95054	ACIES ENGINEERING	Mechanical Project Director	33-21 31 17 31 01
      |56f124dfd5d8ad25b1325b3e	Prabhas		Kejriwal		prabhas@buildwhiz.com	prabhas@stanfordalumni.com		(000) 000-0000			Address-Full	Address-Street	Address-City	CA	Address-ZIP	Buildwhiz	Owner	33-21,34-55 14 19 XX,34-55 14 19 YY
      |56f124dfd5d8ad25b1325b3f	Tester		Tester		tester@buildwhiz.com			(000) 000-0000			Address-Full	Address-Street	Address-City	CA	Address-ZIP	Buildwhiz	Tester	33-21
      |56f124dfd5d8ad25b1325b41	No		One		tester@buildwhiz.com			(000) 000-0000			Address-Full	Address-Street	Address-City	CA	Address-ZIP	Buildwhiz	Tester	33-21
      |56f124dfd5d8ad25b1325b40	Sanjay		Dasgupta		sanjay.dasgupta@buildwhiz.com			(000) 000-0000			Address-Full	Address-Street	Address-City	WB	700068	Buildwhiz	Software Engineer	33-21,34-55 14 19 XX""".stripMargin

  //, "", "", "56f124dfd5d8ad25b1325b3c"
  // 57074085d5d8ad1cf3f767d0, 57074085d5d8ad1cf3f767d1, 57074085d5d8ad1cf3f767d2, 57074085d5d8ad1cf3f767d3, 57074085d5d8ad1cf3f767d4, 57074085d5d8ad1cf3f767d5, 57074085d5d8ad1cf3f767d6, 57074085d5d8ad1cf3f767d7, 57074085d5d8ad1cf3f767d8, 57074085d5d8ad1cf3f767d9
//  private val personIds = Seq(
//    "", "", "", "",
//    "", "", "", "",
//    "", "", "", "",
//    "", "", "", "",
//    "", "", "", "",
//    "", "", "", "",
//    "", ""
//  )

  //private val personOids: ObjectIdList = personIds.map(id => new ObjectId(id))

  private def reStructureEmailsAndPhones(document: Document): Unit = {
    val phones = document("phones").asInstanceOf[Document]
    val newPhones: Seq[Map[String, AnyRef]] = phones.keys.toSeq.map(key => Map("type" -> key, "phone" -> phones(key)))
    val newPhoneDocs: DocumentList = newPhones.map(v => {val d: Document = v; d})
    document("phones") = newPhoneDocs

    val emails = document("emails").asInstanceOf[Document]
    val newEmails: Seq[Map[String, AnyRef]] = emails.keys.toSeq.map(key => Map("type" -> key, "email" -> emails(key)))
    val newEmailDocs: DocumentList = newEmails.map(v => {val d: Document = v; d})
    document("emails") = newEmailDocs
  }

  private def processData(): Unit = {
    val fileLines = data.split("\n")
    val fieldNames = fileLines.head.split("\t").tail
    for (personData <- fileLines.tail/*.zip(personOids)*/) {
      val recordFields = personData.split("\t")
      val fieldValues = recordFields.tail
      //println(fieldValues.length)
      val personOid = new ObjectId(recordFields.head)
      val pairs = ("_id" -> personOid) +:  fieldNames.filterNot(_.startsWith("business")).zip(fieldValues)
      val newBsonDoc: Document = pairs.foldLeft(mutable.Map.empty[String, AnyRef])((mmap, p) => {
        if (p._1.contains('.')) {
          val names = p._1.split('.')
          if (mmap.contains(names(0))) {
            val oldValue = mmap(names(0)).asInstanceOf[Map[String, AnyRef]]
            mmap(names(0)) = oldValue ++ Map(names(1) -> p._2)
          } else {
            mmap(names(0)) = Map(names(1) -> p._2)
          }
        } else {
          mmap(p._1) = p._2
        }
        mmap
      }).toMap
      // retain previously defined projects if any
      val projectIds = BWMongoDB3.persons.find(Map("_id" -> personOid)).headOption match {
        case None => new java.util.ArrayList[ObjectId]
        case Some(d) => d("project_ids").asInstanceOf[ObjectIdList]
      }
      newBsonDoc("project_ids") = projectIds
      // passwords for testing
      val firstName = newBsonDoc("first_name").asInstanceOf[String]
      val password = if (firstName.matches("Prabhas|Sanjay|Tester")) "abc" else firstName
      newBsonDoc("password") = md5(password)
      val roles = new java.util.ArrayList[String]
      if (newBsonDoc.containsKey("role")) {
        roles.addAll(newBsonDoc("role").asInstanceOf[String].split(",").toSeq)
      }
      newBsonDoc("omniclass34roles") = roles
      reStructureEmailsAndPhones(newBsonDoc)
      val result = BWMongoDB3.persons.replaceOne(Map("_id" -> personOid), newBsonDoc)
      if (result.getMatchedCount == 0) {
        BWMongoDB3.persons.insertOne(newBsonDoc)
      } /*else if (result.getModifiedCount == 0) {
        throw new IllegalArgumentException(s"MongoDB error: $result")
      }*/
    }
  }

  private def replaceProjectsInPersons(): Unit = {
    val projects: Seq[DynDoc] = BWMongoDB3.projects.find().toSeq
    for (project <- projects) {
      val adminPersonOId = project.admin_person_id[ObjectId]
      BWMongoDB3.persons.updateOne(Map("_id" -> adminPersonOId),
        Map("$addToSet" -> Map("project_ids" -> project._id[ObjectId])))
    }
  }

//  val initialCount = BWMongoDB3.persons.count()
//  val result = BWMongoDB3.persons.deleteMany(Map("_id" -> Map("$in" -> personOids)))
  val initialCount = BWMongoDB3.persons.count()
  processData()
  val finalCount = BWMongoDB3.persons.count()
  replaceProjectsInPersons()
  BWLogger.log(getClass.getName, s"person-counts: $initialCount, $finalCount", "EXIT")

}
