package cn.hollis.llm.mentor.rag.neo4j.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

//导演、电影关系
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class DirectorMoviesDto {

    private String director;

    private List<String> otherMovies;
}
