// WITH_STDLIB
// PROBLEM: none

class Foo {
    fun s(a: String) {}

    fun test() {
        Bar().apply {
            <caret>this@Foo.s("")
        }
    }
}

class Bar {
    fun s(s: String) {}
}

