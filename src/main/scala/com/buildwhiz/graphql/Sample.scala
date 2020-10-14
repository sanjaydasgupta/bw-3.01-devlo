package com.buildwhiz.graphql

import com.google.common.collect.ImmutableMap
import graphql.GraphQL
import graphql.schema.idl.{RuntimeWiring, SchemaGenerator, SchemaParser, TypeRuntimeWiring}
import graphql.schema.{DataFetcher, DataFetchingEnvironment}

object Sample extends App {
  private val books = Seq(
    ImmutableMap.of("id", "book-1", "name", "Harry Potter and the Philosopher's Stone", "pageCount", "223", "authorId", "author-1"),
    ImmutableMap.of("id", "book-2", "name", "Moby Dick", "pageCount", "635", "authorId", "author-n"),
    ImmutableMap.of("id", "book-3", "name", "Interview with the vampire", "pageCount", "371", "authorId", "author-3")
  )

  private val authors = Seq(
    ImmutableMap.of("id", "author-1", "firstName", "Joanne", "lastName", "Rowling"),
    ImmutableMap.of("id", "author-2", "firstName", "Herman", "lastName", "Melville"),
    ImmutableMap.of("id", "author-3", "firstName", "Anne", "lastName", "Rice"),
    ImmutableMap.of("id", "author-n", "firstName", "Sanjay", "lastName", "Dasgupta")
  )

  object getBookByIdDataFetcher extends DataFetcher[ImmutableMap[String, String]] {
    def get(dfe: DataFetchingEnvironment): ImmutableMap[String, String] = {
      val bookId = dfe.getArgument[String]("id")
      books.find(_.get("id") == bookId) match {
        case Some(book) => book
        case None => null
      }
    }
  }

  object getAuthorByIdDataFetcher extends DataFetcher[ImmutableMap[String, String]] {
    def get(dfe: DataFetchingEnvironment): ImmutableMap[String, String] = {
      val authorId = dfe.getSource[ImmutableMap[String, String]].get("authorId")
      println(s"authorId: $authorId")
      authors.find(_.get("id") == authorId) match {
        case Some(author) => author
        case None => null
      }
    }
  }

  val runTimeWiring = RuntimeWiring.newRuntimeWiring.
      `type`(TypeRuntimeWiring.newTypeWiring("Query").dataFetcher("bookById", getBookByIdDataFetcher)).
      `type`(TypeRuntimeWiring.newTypeWiring("Book").dataFetcher("author", getAuthorByIdDataFetcher)).build

  val schema = """type Query {
                 |  bookById(id: ID): Book
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
  //val graphQLDataFetchers = new GraphQLDataFetchers()
  val typeRegistry = new SchemaParser().parse(schema)
  //println(typeRegistry.getType("Author"))
  //println(typeRegistry.getType("Book"))

  val graphQLSchema = new SchemaGenerator().makeExecutableSchema(typeRegistry, runTimeWiring)
  println(graphQLSchema.getQueryType)
  //val queryType = graphQLSchema.getQueryType
  //val fieldDefinition = queryType.getFieldDefinitions.get(0)
  //println(fieldDefinition.getChildren)
  //println(fieldDefinition.getName)
  //println(fieldDefinition.getType)
  //println(fieldDefinition.getArguments.get(0))

  val graphQl = GraphQL.newGraphQL(graphQLSchema).build()
  print(graphQl)
  print(graphQl.execute("""{
                          |  bookById(id: "book-2"){
                          |    id
                          |    name
                          |    pageCount
                          |    author {
                          |      firstName
                          |      lastName
                          |    }
                          |  }
                          |}""".stripMargin))
}
