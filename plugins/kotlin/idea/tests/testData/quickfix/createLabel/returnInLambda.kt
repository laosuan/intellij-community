// "Create label foo@" "true"

inline fun Int.bar(f: (Int) -> Unit) { }

fun test() {
    1.bar { if (it == 2) return@<caret>foo }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLambda
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.CreateLabelFix$ForLambda