package com.whisper.aster.compiler.symbol

import com.google.devtools.ksp.findActualType
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.kotlinpoet.ClassName

/**
 * 判断目标类型及其外层类型能否被生成到独立包中的 Registry 引用.
 *
 * @return 目标类型是否对当前模块的生成代码可见.
 */
internal fun KSClassDeclaration.isAccessibleFromGeneratedRegistry(): Boolean {
    var current: KSClassDeclaration? = this
    while (current != null) {
        val visibility: Visibility = current.getVisibility()
        if (visibility != Visibility.PUBLIC && visibility != Visibility.INTERNAL) {
            return false
        }
        current = current.parentDeclaration as? KSClassDeclaration
    }
    return true
}

internal fun KSClassDeclaration.stringArgument(
    annotationName: String,
    argumentName: String
): String? {
    return annotationArgument(annotationName, argumentName) as? String
}

internal fun KSClassDeclaration.booleanArgument(
    annotationName: String,
    argumentName: String
): Boolean? {
    return annotationArgument(annotationName, argumentName) as? Boolean
}

private fun KSClassDeclaration.annotationArgument(
    annotationName: String,
    argumentName: String
): Any? {
    val annotation: KSAnnotation = annotations.firstOrNull { annotation: KSAnnotation ->
        annotation.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
    } ?: return null
    return annotation.arguments
        .firstOrNull { argument -> argument.name?.asString() == argumentName }
        ?.value
}

internal fun KSClassDeclaration.isSubclassOf(
    targetClassName: String,
    visitedClassNames: MutableSet<String> = mutableSetOf()
): Boolean {
    val currentClassName: String = qualifiedName?.asString() ?: return false
    if (currentClassName == targetClassName) {
        return true
    }
    if (!visitedClassNames.add(currentClassName)) {
        return false
    }

    return superTypes.any { superType ->
        val resolvedDeclaration: KSDeclaration = superType.resolve().declaration
        val declaration: KSClassDeclaration? = when (resolvedDeclaration) {
            is KSClassDeclaration -> resolvedDeclaration
            is KSTypeAlias -> resolvedDeclaration.findActualType()
            else -> null
        }
        declaration?.isSubclassOf(targetClassName, visitedClassNames) == true
    }
}

internal fun String.hasSegment(segment: String): Boolean {
    return startsWith("/$segment/") || startsWith("$segment.")
}

internal fun KSClassDeclaration.asKotlinPoetClassName(): ClassName {
    val simpleNames: MutableList<String> = mutableListOf()
    var current: KSClassDeclaration? = this
    while (current != null) {
        simpleNames += current.simpleName.asString()
        current = current.parentDeclaration as? KSClassDeclaration
    }
    simpleNames.reverse()
    return ClassName(packageName.asString(), simpleNames)
}
