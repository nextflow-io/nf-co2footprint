import java.security.InvalidKeyException

String message = "No match found for '${model}'. Fallback to default set to `${fallbackToDefault}`."
log.error(message)
throw new InvalidKeyException(message)