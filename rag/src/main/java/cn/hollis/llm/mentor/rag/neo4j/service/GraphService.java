package cn.hollis.llm.mentor.rag.neo4j.service;

import cn.hollis.llm.mentor.rag.neo4j.model.DirectorMoviesDto;
import cn.hollis.llm.mentor.rag.neo4j.repo.MovieGraphRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GraphService {

    @Autowired
    private MovieGraphRepository repository;

    // 根据电影名称查询该电影的导演，并返回该导演执导的其他电影
    public String retrieveContext(String movieName) {
        // 查询与给定电影同导演的其他电影
        List<DirectorMoviesDto> results = repository.findOtherMoviesBySameDirector(movieName);

        if (results.isEmpty()) {
            return "未找到导演过《" + movieName + "》的导演的其他作品。";
        }

        StringBuilder sb = new StringBuilder();
        for (DirectorMoviesDto dto : results) {
            String director = dto.getDirector();
            List<String> movies = dto.getOtherMovies();
            sb.append(String.format("- 导演 %s 还执导了：%s\n", director, String.join("、", movies)));
        }
        return sb.toString().trim();

    }

}