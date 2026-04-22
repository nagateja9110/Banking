error id: file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/AccountNotFoundException.java:java/lang/RuntimeException#
file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/AccountNotFoundException.java
empty definition using pc, found symbol in pc: java/lang/RuntimeException#
empty definition using semanticdb
empty definition using fallback
non-local guesses:

offset: 92
uri: file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/AccountNotFoundException.java
text:
```scala
package com.hdfc.banking.exception;

public class AccountNotFoundException extends RuntimeEx@@ception {
    public AccountNotFoundException(Long id) {
        super("Account with ID" + id + "Not Found");
    }

    public AccountNotFoundException(String accountNumber) {
        super("Account with number " + accountNumber + " not found");
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: java/lang/RuntimeException#