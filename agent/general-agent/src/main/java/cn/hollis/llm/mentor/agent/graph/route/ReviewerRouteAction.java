package cn.hollis.llm.mentor.agent.graph.route;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;

//
public class ReviewerRouteAction implements EdgeAction {

    // 最大修订轮次防止无限循环
    private static final int MAX_REVISIONS = 3;

    @Override
    public String apply(OverAllState state) {
        boolean approved = state.value("approved", false);
        int revisionCount = state.value("revisionCount", 0);

        // 通过审核或达到上限后结束流程
        if (approved || revisionCount >= MAX_REVISIONS) {
            return "end";
        }
        // 未通过则回到 writer 节点继续修订
        return "writer";
    }
}
