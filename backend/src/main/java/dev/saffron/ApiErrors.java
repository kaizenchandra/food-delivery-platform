package dev.saffron;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import static org.springframework.http.HttpStatus.*;
@RestControllerAdvice class ApiErrors {
 @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(BAD_REQUEST) Map<String,String> invalid(IllegalArgumentException e){return Map.of("error",e.getMessage()==null?"Invalid request":e.getMessage());}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class) @ResponseStatus(CONFLICT)Map<String,String> conflict(){return Map.of("error","Concurrent allocation or resource conflict; refresh and retry");}
}
