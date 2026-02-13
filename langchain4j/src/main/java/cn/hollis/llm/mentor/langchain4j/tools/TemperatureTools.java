package cn.hollis.llm.mentor.langchain4j.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * 温度查询工具示例，用于演示 Tool Calling。
 */
public class TemperatureTools {

    /**
     * 根据城市和日期查询温度。
     */
    @Tool(value = "Get temperature by city and date", name = "getTemperatureByCityAndDate")
    public String getTemperatureByCityAndDate(@P("city for get Temperature") String city,
                                              @P("date for get Temperature") String date) {
        System.out.println("getTemperatureByCityAndDate invoke...");
        return "23摄氏度";
    }
}
