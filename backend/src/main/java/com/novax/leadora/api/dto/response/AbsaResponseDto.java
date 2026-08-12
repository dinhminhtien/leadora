package com.novax.leadora.api.dto.response;

public record AbsaResponseDto(
    AspectResultDto attitude,
    AspectResultDto speed,
    AspectResultDto accuracy,
    AspectResultDto facility,
    AspectResultDto price
) {}
