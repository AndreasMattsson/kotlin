/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve.transformers

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.diagnostics.ConeSimpleDiagnostic
import org.jetbrains.kotlin.fir.diagnostics.DiagnosticKind
import org.jetbrains.kotlin.fir.resolve.ScopeSession
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutor
import org.jetbrains.kotlin.fir.resolve.substitution.ConeSubstitutorByMap
import org.jetbrains.kotlin.fir.resolve.toSymbol
import org.jetbrains.kotlin.fir.scopes.FirCompositeScope
import org.jetbrains.kotlin.fir.scopes.FirScope
import org.jetbrains.kotlin.fir.scopes.impl.FirMemberTypeParameterScope
import org.jetbrains.kotlin.fir.types.ConeClassErrorType
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.ConeLookupTagBasedType

abstract class FirAbstractTreeTransformerWithSuperTypes(
    phase: FirResolvePhase,
    protected val scopeSession: ScopeSession
) : FirAbstractTreeTransformer<Any?>(phase) {
    protected val scopes = mutableListOf<FirScope>()
    protected val towerScope = FirCompositeScope(scopes.asReversed())

    protected open fun needReplacePhase(firDeclaration: FirDeclaration): Boolean = transformerPhase > firDeclaration.resolvePhase

    protected inline fun <T> withScopeCleanup(crossinline l: () -> T): T {
        val sizeBefore = scopes.size
        val result = l()
        val size = scopes.size
        assert(size >= sizeBefore)
        repeat(size - sizeBefore) {
            scopes.removeAt(scopes.lastIndex)
        }
        return result
    }

    protected fun FirMemberDeclaration.addTypeParametersScope() {
        if (typeParameters.isNotEmpty()) {
            scopes.add(FirMemberTypeParameterScope(this))
        }
    }

    open fun transformDeclarationContent(declaration: FirDeclaration, data: Any?): FirDeclaration {
        return transformElement(declaration, data)
    }
}

fun createSubstitutionForSupertype(superType: ConeLookupTagBasedType, session: FirSession): ConeSubstitutor {
    val klass = superType.lookupTag.toSymbol(session)?.fir as? FirRegularClass ?: return ConeSubstitutor.Empty
    val arguments = superType.typeArguments.map {
        it as? ConeKotlinType ?: ConeClassErrorType(ConeSimpleDiagnostic("illegal projection usage", DiagnosticKind.IllegalProjectionUsage))
    }
    val mapping = klass.typeParameters.map { it.symbol }.zip(arguments).toMap()
    return ConeSubstitutorByMap(mapping, session)
}
