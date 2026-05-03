package com.examsolver.service.ai;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;

/**
 * Interface định nghĩa contract cho AI Question Solver.
 * Có thể implement nhiều provider khác nhau (Claude, GPT, Gemini...).
 */
public interface AiSolverService {

    /**
     * Giải câu hỏi và trả về đáp án theo đúng định dạng response.
     *
     * @param request payload câu hỏi từ client
     * @return SolveResponse với đáp án đúng định dạng
     */
    SolveResponse solveQuestion(SolveRequest request);

    /**
     * Tên provider AI (để logging / tracking)
     */
    String getProviderName();
}
