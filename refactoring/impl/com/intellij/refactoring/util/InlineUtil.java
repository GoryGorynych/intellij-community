package com.intellij.refactoring.util;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.codeInspection.redundantCast.RedundantCastUtil;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author ven
 */
public class InlineUtil {

  public static PsiExpression inlineVariable(PsiLocalVariable variable, PsiExpression initializer, PsiJavaCodeReferenceElement ref)
    throws IncorrectOperationException {
    PsiManager manager = initializer.getManager();

    PsiClass thisClass = RefactoringUtil.getThisClass(initializer);
    PsiClass refParent = RefactoringUtil.getThisClass(ref);
    final PsiType varType = variable.getType();
    initializer = RefactoringUtil.convertInitializerToNormalExpression(initializer, varType);

    ChangeContextUtil.encodeContextInfo(initializer, false);
    PsiExpression expr = (PsiExpression)ref.replace(initializer);
    PsiType exprType = expr.getType();
    if (exprType != null && !varType.equals(exprType)) {
      boolean matchedTypes = false;
      //try explicit type arguments
      final PsiElementFactory elementFactory = manager.getElementFactory();
      if (expr instanceof PsiCallExpression && ((PsiCallExpression)expr).getTypeArguments().length == 0) {
        final JavaResolveResult resolveResult = ((PsiCallExpression)initializer).resolveMethodGenerics();
        final PsiElement resolved = resolveResult.getElement();
        if (resolved instanceof PsiMethod) {
          final PsiTypeParameter[] typeParameters = ((PsiMethod)resolved).getTypeParameters();
          if (typeParameters.length > 0) {
            final PsiCallExpression copy = (PsiCallExpression)expr.copy();
            for (final PsiTypeParameter typeParameter : typeParameters) {
              final PsiType substituted = resolveResult.getSubstitutor().substitute(typeParameter);
              if (substituted == null) break;
              copy.getTypeArgumentList().add(elementFactory.createTypeElement(substituted));
            }
            if (varType.equals(copy.getType())) {
              ((PsiCallExpression)expr).getTypeArgumentList().replace(copy.getTypeArgumentList());
              matchedTypes = true;
            }
          }
        }
      }

      if (!matchedTypes) {
        //try cast
        PsiTypeCastExpression cast = (PsiTypeCastExpression)elementFactory.createExpressionFromText("(t)a", null);
        PsiTypeElement castTypeElement = cast.getCastType();
        assert castTypeElement != null;
        castTypeElement.replace(variable.getTypeElement());
        final PsiExpression operand = cast.getOperand();
        assert operand != null;
        operand.replace(expr);
        PsiExpression exprCopy = (PsiExpression)expr.copy();
        cast = (PsiTypeCastExpression)expr.replace(cast);
        if (!RedundantCastUtil.isCastRedundant(cast)) {
          expr = cast;
        }
        else {
          PsiElement toReplace = cast;
          while (toReplace.getParent() instanceof PsiParenthesizedExpression) {
            toReplace = toReplace.getParent();
          }
          expr = (PsiExpression)toReplace.replace(exprCopy);
        }
      }
    }

    ChangeContextUtil.clearContextInfo(initializer);

    PsiThisExpression thisAccessExpr = null;
    if (Comparing.equal(thisClass, refParent))

    {
      thisAccessExpr = RefactoringUtil.createThisExpression(manager, null);
    }

    else

    {
      if (!(thisClass instanceof PsiAnonymousClass)) {
        thisAccessExpr = RefactoringUtil.createThisExpression(manager, thisClass);
      }
    }

    return (PsiExpression)ChangeContextUtil.decodeContextInfo(expr, thisClass, thisAccessExpr);
  }
}
