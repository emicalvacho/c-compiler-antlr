package threeAddressCode;

import java.util.Collection;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.Trees;

import app.reglasBaseVisitor;
import app.reglasParser;
import app.reglasParser.*;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ThreeAddressCodeVisitor extends reglasBaseVisitor<String> {
    private int countLbl;
    private int countTmp;
    private String result;
    private String previousTemp;
    private String currentTemp;

    public ThreeAddressCodeVisitor() {
        this.countLbl = 0;
        this.countTmp = 0;
        this.result = "";
        this.previousTemp = "";
        this.currentTemp = "";
    }

    @Override
    public String visit(ParseTree tree) {
        return super.visit(tree);
    }

    @Override
    public String visitAssignment(AssignmentContext ctx) {
        if (ctx.asign() != null) {
            List<ParseTree> ruleFactors = findRuleNodes(ctx, reglasParser.RULE_factor);
            List<ParseTree> funcs = findRuleNodes(ctx, reglasParser.RULE_callfunction);
            if (ruleFactors.size()<3 && funcs.size() == 0) {
                result += ctx.ID().getText() + " = ";
                moreThanTwo(ruleFactors);
            } else{
                processConjunctions(ctx.asign().operation().opal());
                result += ctx.ID().getText() + " = t" + (countTmp - 1) + "\n";
            }
        
        }
        return "";
    }

    @Override
    public String visitDeclaration(DeclarationContext ctx) {
        if (ctx.asign() != null) {
            List<ParseTree> ruleFactors = findRuleNodes(ctx, reglasParser.RULE_factor);
            if (ruleFactors.size()<3) {
                result += ctx.ID().getText() + " = ";
                moreThanTwo(ruleFactors);
            } else{
                processConjunctions(ctx.asign().operation().opal());
                result += ctx.ID().getText() + " = t" + (countTmp - 1) + "\n";
            }
        }
        return "";
    }

    @Override
    public String visitCondif(CondifContext ctx) {
        countLbl++;
        processConjunctions(ctx.operation().opal());
        result += "ifnot " + currentTemp + ", jmp L" + countLbl + "\n";
        if (ctx.ELSE() == null) {
            visitChildren(ctx);
        } else {

            // bloque if
            visitBlock((BlockContext) ctx.getChild(4));

            int aux = countLbl;
            countLbl++;
            result += String.format("jmp L%s\n", countLbl);
            result += String.format("label L%s\n", aux);

            // bloque else
            visitBlock((BlockContext) ctx.getChild(6));
        }
        result += String.format("label L%s\n", countLbl);

        return "";
    }

    @Override
    public String visitCyclewhile(CyclewhileContext ctx) {
        countLbl++;
        int aux = countLbl;

        result += String.format("label L%s\n", countLbl);
        countLbl++;
        result += String.format("ifnot %s, jmp L%s\n", ctx.operation().getText(), countLbl);

        visitChildren(ctx);

        result += String.format("jmp L%s\n", aux);
        result += String.format("label L%s\n", countLbl);

        return "";
    }

    @Override
    public String visitBlock(BlockContext ctx) {
        visitChildren(ctx);
        return "";
    }

    @Override
    public String visitCyclefor(CycleforContext ctx) {
        countLbl++;

        visitAssignment(ctx.assignment());

        int aux = countLbl;
        result += String.format("label L%s\n", countLbl);
        countLbl++;
        result += String.format("ifnot %s, jmp L%s\n", ctx.operation().getText(), countLbl);
        visitBlock(ctx.instruction().block());

        result += String.format("%s %s\n", ctx.ID().getText(), ctx.asign().getText());
        result += String.format("jmp L%s\n", aux);
        result += String.format("label L%s\n", countLbl);

        return "";
    }

    @Override
    public String visitFunction(FunctionContext ctx){ 
        result += String.format("func begin %s\n",ctx.ID().getText());
        visitBlock(ctx.block());
        result += String.format("%s end\n",ctx.ID().getText());
        return "";
    }

    @Override
    public String visitRetorno(RetornoContext ctx){
        processConjunctions(ctx.opal());
        result += String.format("return %s\n", currentTemp);
        return "";
    }

    @Override
    public String visitCallfunction(CallfunctionContext ctx){
        if(ctx.arguments().operation().opal().getChildCount() > 0){
            List<ParseTree> args = findRuleNodes(ctx, reglasParser.RULE_operation);
            for (ParseTree a : args) {
                processConjunctions(((OperationContext)a).opal());
                result += String.format("param %s\n",currentTemp);
            }
            result += String.format("t%d = call %s, %d\n",countTmp,ctx.ID().getText(),args.size());
        } else{
            result += String.format("t%d = call %s\n",countTmp,ctx.ID().getText());
        }
        countTmp++;
        return "";
    }

    private List<ParseTree> findRuleNodes(ParseTree ctx, int ruleIndex){
        return new ArrayList<ParseTree>(Trees.findAllRuleNodes(ctx, ruleIndex));
    }

    public void getResult() {
        System.out.println(result);
    }

    // Esta funcion concate los temporales anteriores y actuales pasandole la
    // operacion entre medio
    private void concatTemps(String operation) {
        result += String.format("t%d = %s %s %s \n", countTmp, previousTemp, operation, currentTemp);
        currentTemp = "t" + countTmp;
        countTmp++;
    }

    public void findRuleNodesWithoutOpal(ParseTree t, int index, List<ParseTree> nodes) {
        if (t instanceof ParserRuleContext) {
            ParserRuleContext ctx = (ParserRuleContext) t;
            if (ctx.getRuleIndex() == index) {
                nodes.add(t);
            }
        }
        // check children
        for (int i = 0; i < t.getChildCount(); i++) {
            if (!(t.getChild(i) instanceof OpalContext)) {
                findRuleNodesWithoutOpal(t.getChild(i), index, nodes);
            }
        }
    }

    public void generateCode() {
        try {
            FileWriter fileWriter = new FileWriter("intermediate-code.txt");
            for(int i=0;i<result.length();i++){
                fileWriter.write(result.charAt(i));
            }
            fileWriter.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
    
    private void moreThanTwo(List<ParseTree> ruleFactors){
        for (ParseTree parseTree : ruleFactors) {
            FactorContext fc = ((FactorContext)parseTree);
            if(fc.getParent().getParent() instanceof ExpContext){
                result += fc.getParent().getParent().getChild(0).getText() + " " + fc.getParent().getChild(0).getText() + "\n";
            } else if(fc.getParent() instanceof TerContext){
                result += fc.getParent().getChild(0).getText() + " " + fc.getText() + "\n";   
            } else {
                result += fc.getParent().getChild(0).getText() + (ruleFactors.size() == 1 ? "\n" : " ");
            }
        }
    }

    private void processConjunctions(OpalContext ctx) {
        List<ParseTree> conjunctions = new ArrayList<ParseTree>();
        findRuleNodesWithoutOpal(ctx, reglasParser.RULE_conjunction, conjunctions);
        String temp;
        for (int i = 0; i < conjunctions.size(); i++) {
            temp = currentTemp;
            processComparisons((ConjunctionContext) conjunctions.get(i));
            previousTemp = temp;
            // currentTemp = temp;
            if (i > 0) {
                concatTemps(conjunctions.get(i).getParent().getChild(0).getText());
            }
        }

    }

    private void processComparisons(ConjunctionContext ctx) {
        List<ParseTree> comparisons = new ArrayList<ParseTree>();
        findRuleNodesWithoutOpal(ctx, reglasParser.RULE_comparison, comparisons);
        String temp;
        for (int i = 0; i < comparisons.size(); i++) {
            temp = currentTemp;
            processExpressions((ComparisonContext) comparisons.get(i));
            previousTemp = temp;
            // currentTemp = temp;
            if (i > 0) {
                concatTemps(comparisons.get(i).getParent().getChild(0).getText());
            }
        }
    }

    private void processExpressions(ComparisonContext ctx) {
        List<ParseTree> exps = new ArrayList<ParseTree>();
        findRuleNodesWithoutOpal(ctx, reglasParser.RULE_expression, exps);
        String temp;
        for (int i = 0; i < exps.size(); i++) {
            temp = currentTemp;
            processTerms((ExpressionContext) exps.get(i));
            previousTemp = temp;
            // currentTemp = temp;
            if (i > 0) {
                concatTemps(exps.get(i).getParent().getChild(0).getText());
            }
        }
    }

    private void generateTempsInTerm(Collection<ParseTree> factors) {
        List<ParseTree> factorsLocal = new ArrayList<ParseTree>(factors);
        String temp;
        for(int i=0; i < factorsLocal.size(); i++){
            if(((FactorContext)factorsLocal.get(i)).opal() != null){
                temp = currentTemp;
                processConjunctions(((FactorContext) factorsLocal.get(i)).opal());
                previousTemp = temp;
                // currentTemp = "t" + (countTmp - 1);
            } else if(((FactorContext)factorsLocal.get(i)).callfunction() != null){
                temp = currentTemp;
                visitCallfunction(((FactorContext)factorsLocal.get(i)).callfunction());
                previousTemp = temp;
                currentTemp = "t" + (countTmp - 1);
            } else {
                // sino guardar el valor
                previousTemp = currentTemp;
                currentTemp = factorsLocal.get(i).getText();
            }
            if (i > 0) {
                concatTemps(factorsLocal.get(i).getParent().getChild(0).getText());
            }
        }
    }

    private void processTerms(ExpressionContext ctx) {
        List<ParseTree> ruleTerms = new ArrayList<ParseTree>();
        findRuleNodesWithoutOpal(ctx, reglasParser.RULE_term, ruleTerms);
        String temp;

        List<ParseTree> terms = new ArrayList<ParseTree>(ruleTerms);
        for (int i = 0; i < terms.size(); i++) {
            // Lista de factores de ese termino 'i' 9 * 8 / 2 -> [9,8,2]
            List<ParseTree> factors = new ArrayList<ParseTree>();
            findRuleNodesWithoutOpal(terms.get(i), reglasParser.RULE_factor, factors);

            // Si tiene mas de un factor -> 9 * 8 / 2
            if (factors.size() > 1) {
                temp = currentTemp;
                generateTempsInTerm(factors); // Genero los temporales
                // t0 = 9 * 8
                // t1 = t0 / 2

                previousTemp = temp; // almaceno en un auxiliar el temporal actual

                currentTemp = "t" + (countTmp - 1);
            } else {
                previousTemp = currentTemp; // almaceno en un auxiliar el temporal actual
                if (((TermContext) terms.get(i)).factor().opal() != null) {
                    temp = currentTemp;
                    processConjunctions(((TermContext) terms.get(i)).factor().opal());
                    previousTemp = temp;
                }else if(((TermContext)terms.get(i)).factor().callfunction() != null){
                    temp = currentTemp;
                    visitCallfunction(((TermContext) terms.get(i)).factor().callfunction());
                    previousTemp = temp;
                    currentTemp = "t" + (countTmp - 1);
                }
                else {
                    currentTemp = factors.get(0).getText(); // el actual es el primero de la lista 9 -> 9 + 1
                }
            }
            if(i > 0){ 
                concatTemps(terms.get(i).getParent().getChild(0).getText());
            }
        }
    }
}

/*
 * - cuando los terminos son mayores o iguales que 3 - agregar temporales (ok) -
 * propiedad distributiva no se hace bien - (9 * 8) * 4 --> No imprime el 4 - 4
 * * (9 * 8) --> No imprime bien el termino (Sugerencia Joseniana es el
 * generarTemps())
 * 
 * - asignacion - if - while - for - funciones
 * 
 * - cuando el opal es el unico termino x = ( 9 + 1 ) x = 4 + ( 9 * 2) + 5 no
 * esta contemplado el proceso de un unico termino
 * 
 * - el igual no hace falta imprimirlo viene con la regla - guardar en un
 * archivo
 * 
 * int main(){ int x; int y = 0; --> y = 0 int z = 1; --> z = 1
 * 
 * x = y + z; --> x = y + z
 * 
 * if (x < 0){ --> ifnot x < 0, jmp L1 x = x + 1; --> x = x + 1 --> jmp L2 }
 * else{ --> label L1 x = x + 2; --> x = x + 2 } --> label L2
 * 
 * --> label L3 while(x < 0){ --> ifnot x < 0, jmp L4 x = x + 1; --> x = x + 1
 * --> jmp L3 } --> label L4
 * 
 * int i;
 * 
 * for(i=0; i<10 ; i=i+1){ --> i = 0 --> label L5 --> ifnot i<10, jmp L6 x = x +
 * 1; --> x = x + 1 --> i = i + 1 } --> jmp L5 --> label L6
 * 
 * y = (10 + x) + 19 * y + z * 7 --> t2 = 10 + x --> t3 = 19 * y --> t4 = z * 7
 * --> t5 = t2 + t3 --> y = t5 + t4 }
 
    - cuando los terminos son mayores o iguales que 3
        - agregar temporales (ok)
        - propiedad distributiva no se hace bien
            - (9 * 8) * 4  --> No imprime el 4
            - 4 * (9 * 8)  --> No imprime bien el termino
            (Sugerencia Joseniana es el generarTemps())

    - asignacion
    - if
    - while
    - for
    - funciones

    int main(){     
        int x;                              
        int y = 0;                          --> y = 0
        int z = 1;                          --> z = 1

        x = y + z;                          --> x = y + z

        if (x < 0){                         --> ifnot x < 0, jmp L1
            x = x + 1;                      --> x = x + 1
                                            --> jmp L2
        } else{                             --> label L1
            x = x + 2;                      --> x = x + 2
        }                                   --> label L2 
        
                                            --> label L3
        while(x < 0){                       --> ifnot x < 0, jmp L4
            x = x + 1;                      --> x = x + 1          
                                            --> jmp L3
        }                                   --> label L4

        int i;                              

        for(i=0; i<10 ; i=i+1){             --> i = 0
                                            --> label L5
                                            --> ifnot i<10, jmp L6
            x = x + 1;                      --> x = x + 1                        
                                            --> i = i + 1 
        }                                   --> jmp L5
                                            --> label L6

        y = (10 + x) + 19 * y + z * 7       --> t2 = 10 + x
                                            --> t3 = 19 * y
                                            --> t4 = z * 7
                                            --> t5 = t2 + t3
                                            --> y = t5 + t4 
    }
*/
