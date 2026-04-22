error id: file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/GlobalExceptionHandler.java:_empty_/AccountNotFoundException#
file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/GlobalExceptionHandler.java
empty definition using pc, found symbol in pc: _empty_/AccountNotFoundException#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 895
uri: file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/GlobalExceptionHandler.java
text:
```scala
package com.hdfc.banking.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hdfc.banking.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status,String error,String message){
        return ResponseEntity.status(status).body(
            ErrorResponse.builder()
            .status(status.value())
            .error(error)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build()
        );
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse>handleAccountNotFound(AccountNotF@@oundException ex){
        return buildResponse(HttpStatus.NOT_FOUND, "Account Not Found", ex.getMessage());
    }

    


}

```


#### Short summary: 

empty definition using pc, found symbol in pc: _empty_/AccountNotFoundException#