// "Add parameter to function 'bar'" "true"
// WITH_STDLIB
// DISABLE_ERRORS
private val foo = Foo().bar(1, "2", <caret>setOf("3"))