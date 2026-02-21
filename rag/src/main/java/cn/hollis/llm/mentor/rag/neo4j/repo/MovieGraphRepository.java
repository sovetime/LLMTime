package cn.hollis.llm.mentor.rag.neo4j.repo;

import cn.hollis.llm.mentor.rag.neo4j.model.DirectorMoviesDto;
import cn.hollis.llm.mentor.rag.neo4j.model.Movie;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MovieGraphRepository extends Neo4jRepository<Movie, String> {
    // 根据电影标题查找同导演的其他电影
    // 查询逻辑
    // 1 先找到标题为 title 的电影节点 m
    // 2 通过 DIRECTED 关系找到导演节点 d
    // 3 再找到导演 d 执导的其他电影节点 other
    // 4 排除标题与入参相同的电影避免返回当前电影
    // 返回结果
    // director 对应导演姓名
    // otherMovies 对应该导演其他电影列表每项格式为 片名 (年份)
    @Query("""
            MATCH (m:Movie {title: $title}) <-[:DIRECTED]- (d:Director) -[:DIRECTED]-> (other:Movie)
            WHERE other.title <> $title
            RETURN d.name AS director, collect(other.title + ' (' + other.year + ')') AS otherMovies
            """)
    List<DirectorMoviesDto> findOtherMoviesBySameDirector(String title);
}
