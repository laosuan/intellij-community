// "Add 'abstract fun f()' to 'Iterable'" "false"
// ACTION: Make internal
// ACTION: Make private
// ACTION: Make protected
// ACTION: Remove 'override' modifier
// ERROR: 'f' overrides nothing
// ERROR: Class 'B' is not abstract and does not implement abstract member public abstract operator fun iterator(): Iterator<Int> defined in kotlin.collections.Iterable
// WITH_STDLIB
// K2_AFTER_ERROR: 'f' overrides nothing.
// K2_AFTER_ERROR: Class 'B' is not abstract and does not implement abstract member:<br>fun iterator(): Iterator<T>
class B : Iterable<Int> {
    <caret>override fun f() {}
}
