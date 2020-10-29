package com.buildwhiz.graphql

import com.google.common.collect.ImmutableMap
import org.bson.Document
import org.bson.types.ObjectId
import graphql.{ExecutionResult, GraphQL}
import graphql.schema.idl.{RuntimeWiring, SchemaGenerator, SchemaParser, TypeRuntimeWiring}
import graphql.schema.{DataFetcher, DataFetchingEnvironment}
import com.buildwhiz.baf2.PersonApi
import com.buildwhiz.infra.DynDoc
import com.buildwhiz.infra.DynDoc.Many
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import scala.collection.JavaConverters._

object Sample {
  private val books = Seq(
    ImmutableMap.of("id", "book-1", "name", "Harry Potter and the Philosopher's Stone", "pageCount", "223", "authorId", "author-1"),
    ImmutableMap.of("id", "book-2", "name", "Moby Dick", "pageCount", "635", "authorId", "author-n"),
    ImmutableMap.of("id", "book-3", "name", "Interview with the vampire", "pageCount", "371", "authorId", "author-3")
  )

  object getBookById extends DataFetcher[ImmutableMap[String, String]] {
    def get(dfe: DataFetchingEnvironment): ImmutableMap[String, String] = {
      val bookId = dfe.getArgument[String]("id")
      books.find(_.get("id") == bookId) match {
        case Some(book) => book
        case None => null
      }
    }
  }

  private val authors = Seq(
    ImmutableMap.of("id", "author-1", "firstName", "Joanne", "lastName", "Rowling"),
    ImmutableMap.of("id", "author-2", "firstName", "Herman", "lastName", "Melville"),
    ImmutableMap.of("id", "author-3", "firstName", "Anne", "lastName", "Rice"),
    ImmutableMap.of("id", "author-n", "firstName", "Sanjay", "lastName", "Dasgupta")
  )

  object getAuthorById extends DataFetcher[ImmutableMap[String, String]] {
    def get(dfe: DataFetchingEnvironment): ImmutableMap[String, String] = {
      val authorId = dfe.getSource[ImmutableMap[String, String]].get("authorId")
      println(s"authorId: $authorId")
      authors.find(_.get("id") == authorId) match {
        case Some(author) => author
        case None => null
      }
    }
  }

  object getPersonById extends DataFetcher[ImmutableMap[String, Any]] {
    def get(dfe: DataFetchingEnvironment): ImmutableMap[String, Any] = {
      val personId = dfe.getArgument[String]("id")
      val personOid = new ObjectId(personId)
      if (PersonApi.exists(personOid)) {
        val personRecord = PersonApi.personById(personOid)
        val emails: Seq[DynDoc] = personRecord.emails[Many[Document]]
        val workEmail = emails.find(_.`type`[String] == "work").get.email[String]
        val phones: Seq[DynDoc] = personRecord.phones[Many[Document]]
        val workPhone = phones.find(_.`type`[String] == "work").get.phone[String]
        val kv = Map[String, Any]("id" -> personId, "first_name" -> personRecord.first_name[String],
          "last_name" -> personRecord.last_name[String],
          "work_email" -> workEmail, "work_phone" -> workPhone,
          "years_experience" -> personRecord.years_experience[Double])
        ImmutableMap.copyOf(kv.asJava)
      } else {
        null
      }
    }
  }

  object updatePersonById extends DataFetcher[ImmutableMap[String, Any]] {
    def get(dfe: DataFetchingEnvironment): ImmutableMap[String, Any] = {
      val personId = dfe.getArgument[String]("id")
      val otherPhone = dfe.getArgumentOrDefault("other_phone", null)
      val otherEmail = dfe.getArgumentOrDefault("other_email", null)
      val personOid = new ObjectId(personId)
      if (PersonApi.exists(personOid)) {
        val personRecord = PersonApi.personById(personOid)
        val emails: Seq[DynDoc] = personRecord.emails[Many[Document]]
        val workEmail = emails.find(_.`type`[String] == "work").get.email[String]
        val phones: Seq[DynDoc] = personRecord.phones[Many[Document]]
        val workPhone = phones.find(_.`type`[String] == "work").get.phone[String]
        val kv = Map[String, Any]("id" -> personId, "first_name" -> personRecord.first_name[String],
            "last_name" -> personRecord.last_name[String],
            "work_email" -> workEmail, "work_phone" -> workPhone,
            "years_experience" -> personRecord.years_experience[Double])
        ImmutableMap.copyOf(kv.asJava)
      } else {
        null
      }
    }
  }

  private val runTimeWiring = RuntimeWiring.newRuntimeWiring.
      `type`(TypeRuntimeWiring.newTypeWiring("Query").dataFetcher("getBookById", getBookById).
      dataFetcher("getPersonById", getPersonById)).
      `type`(TypeRuntimeWiring.newTypeWiring("Book").dataFetcher("author", getAuthorById)).
      `type`(TypeRuntimeWiring.newTypeWiring("Mutation").dataFetcher("updatePersonById", updatePersonById)).build

  private val schema = """
                 |type Query {
                 |  getBookById(id: ID!): Book
                 |  getPersonById(id: ID!): Person
                 |}
                 |
                 |type Mutation {
                 |  updatePersonById(id: ID!, other_phone: String, other_email: String): Person
                 |}
                 |
                 |type Person {
                 |  id: ID!
                 |  first_name: String!
                 |  last_name: String!
                 |  work_email: String!
                 |  work_phone: String!
                 |  years_experience: Float
                 |}
                 |
                 |type Book {
                 |  id: ID
                 |  name: String
                 |  pageCount: Int
                 |  author: Author
                 |}
                 |
                 |type Author {
                 |  id: ID
                 |  firstName: String
                 |  lastName: String
                 |}""".stripMargin

  private val typeRegistry = new SchemaParser().parse(schema)
  private val graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeRegistry, runTimeWiring)
  //println(graphQLSchema.getQueryType)

  private val graphQl = GraphQL.newGraphQL(graphQLSchema).build()
  //print(graphQl)

  def execute(query: String): ExecutionResult = graphQl.execute(query)
  def execute(query: String, request: HttpServletRequest, response: HttpServletResponse): ExecutionResult = {
      graphQl.execute(query)
  }

  def main(args: Array[String]): Unit = {
    val theBook = execute("""{
                           |  getBookById(id: "book-2"){
                           |    id
                           |    name
                           |    pageCount
                           |    author {
                           |      firstName
                           |      lastName
                           |    }
                           |  }
                           |}""".stripMargin)
    println(new Document(theBook.toSpecification).toJson)
    val thePerson = execute("""{
                           |  getPersonById(id: "56f124dfd5d8ad25b1325b40"){
                           |    id
                           |    first_name
                           |    last_name
                           |    work_email
                           |    work_phone
                           |    years_experience
                           |  }
                           |}""".stripMargin)
    println(new Document(thePerson.toSpecification).toJson)
  }
// %7bbookById%28id%3a%22book-2%22%29%7bid%20name%20pageCount%20author%7bfirstName%20lastName%7d%7d%7d
}
