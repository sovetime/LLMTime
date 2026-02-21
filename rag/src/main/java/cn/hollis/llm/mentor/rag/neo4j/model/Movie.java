package cn.hollis.llm.mentor.rag.neo4j.model;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Movie")
public class Movie {
    @Id
    private String title;

    private int year;

}