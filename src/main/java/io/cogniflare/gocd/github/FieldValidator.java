package io.cogniflare.gocd.github;

import java.util.Map;

public interface FieldValidator {
	void validate(Map<String, Object> fieldValidation);
}
