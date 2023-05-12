# SIlang Interpreter — Version 0.1

Full front-end + interpreter for the SIlang programming language, in Java 21.

## Build & Run

```bash
chmod +x build.sh

# Compile
./build.sh

# Run a program
./build.sh run hello.si

# Print AST (no execution)
./build.sh ast hello.si

# Print tokens (no parsing)
./build.sh tokens hello.si
```

## Manual

```bash
javac --release 21 -d out \
    silang/*.java silang/ast/*.java \
    silang/parser/*.java silang/interpreter/*.java

# Run a program
java -cp out silang.Main hello.si

# Inline source
java -cp out silang.Main --expr 'var x = 5 + 3'
java -cp out silang.Main --expr 'out("Hello, World!")'

# Print AST (no execution)
java -cp out silang.Main --ast hello.si

# Print tokens
java -cp out silang.Main --tokens hello.si
```
