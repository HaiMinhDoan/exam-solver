package com.examsolver.service.ai;

import com.examsolver.dto.request.SolveRequest;
import com.examsolver.dto.response.SolveResponse;
import com.examsolver.entity.PromptVersion;

/**
 * Interface AI solver — có thể swap provider (Claude, GPT, Gemini...).
 */
public interface AiSolverService {

    /**
     * Giải câu hỏi bằng AI với prompt version được chỉ định.
     *
     * @param request     payload câu hỏi
     * @param promptVersion version prompt đang active (null = dùng default template)
     * @return SolveResponse đúng định dạng
     */
    SolveResponse solveQuestion(SolveRequest request, PromptVersion promptVersion);

    String getProviderName();
}