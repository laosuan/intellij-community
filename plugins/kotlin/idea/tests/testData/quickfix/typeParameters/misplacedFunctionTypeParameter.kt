// "Move type parameter constraint to 'where' clause" "true"
fun <<caret>T : Cloneable> foo() where T : Comparable<*> {
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveTypeParameterConstraintFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.MoveTypeParameterConstraintFix