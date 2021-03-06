// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.util

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.java.beans.PropertyKind.*
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType.getUnboxedType
import com.intellij.psi.PsiType

/**
 * This method doesn't check if method name is an accessor name
 */
internal fun PsiMethod.checkKind(kind: PropertyKind): Boolean {
  val expectedParamCount = if (kind === SETTER) 1 else 0
  if (parameterList.parametersCount != expectedParamCount) return false

  if (kind == GETTER && returnType == PsiType.VOID) return false
  if (kind == BOOLEAN_GETTER && !returnType.isBooleanOrBoxed()) return false

  return true
}

private fun PsiType?.isBooleanOrBoxed(): Boolean {
  return this == PsiType.BOOLEAN || getUnboxedType(this) == PsiType.BOOLEAN
}

internal fun String.isPropertyName(): Boolean {
  return GroovyPropertyUtils.isPropertyName(this)
}

/**
 * @return accessor name by [propertyName] assuming it is valid
 */
fun PropertyKind.getAccessorName(propertyName: String): String {
  val suffix = if (propertyName.length > 1 && propertyName[1].isUpperCase()) {
    propertyName
  }
  else {
    propertyName.capitalize()
  }
  return prefix + suffix
}
