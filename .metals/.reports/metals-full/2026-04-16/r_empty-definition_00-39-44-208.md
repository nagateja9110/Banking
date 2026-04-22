error id: file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/AccountNotFoundException.java:local1
file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/AccountNotFoundException.java
empty definition using pc, found symbol in pc: 
found definition using semanticdb; symbol local1
empty definition using fallback
non-local guesses:

offset: 321
uri: file://<WORKSPACE>/src/main/java/com/hdfc/banking/exception/AccountNotFoundException.java
text:
```scala
package com.hdfc.banking.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(Long id) {
        super("Account with ID" + id + "Not Found");
    }

    public AccountNotFoundException(String accountNumber) {
        super("Account with number " + accountNumber@@ + " not found");
    }
}

```


#### Short summary: 

empty definition using pc, found symbol in pc: 