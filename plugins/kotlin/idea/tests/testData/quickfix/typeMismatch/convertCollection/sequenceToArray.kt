// "Convert expression to 'Array' by inserting '.toList().toTypedArray()'" "true"
// WITH_STDLIB

fun foo(a: Sequence<String>) {
    bar(a<caret>)
}

fun bar(a: Array<String>) {}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ConvertCollectionFix