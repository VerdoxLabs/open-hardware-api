package de.verdox.hwapi;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 400 Bad Request mit ProblemDetail */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends RuntimeException {
    private final ProblemDetail problem;
    public BadRequestException(ProblemDetail problem) { super(problem.getDetail()); this.problem = problem; }
    public ProblemDetail getProblem() { return problem; }
}