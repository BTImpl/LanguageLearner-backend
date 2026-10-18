package eu.btimpl.languagelearner.attempts;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What became of an uploaded batch.
 *
 * @param accepted answers saved now
 * @param duplicates answers skipped because the server had them already
 */
@Schema(name = "AttemptsResponse")
public record AttemptsResponse(
    @Schema(description = "Answers saved by this request") int accepted,
    @Schema(description = "Answers skipped because their id was known already") int duplicates) {}
