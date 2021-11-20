package org.codehaus.mojo.antlr.metadata;

import static org.codehaus.mojo.antlr.proxy.Helper.NO_ARGS;
import static org.codehaus.mojo.antlr.proxy.Helper.NO_ARG_SIGNATURE;
import static org.codehaus.plexus.util.StringUtils.isEmpty;
import static org.codehaus.plexus.util.StringUtils.isNotEmpty;
import static org.codehaus.plexus.util.StringUtils.split;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.antlr.Environment;
import org.codehaus.mojo.antlr.proxy.Helper;

/**
 * TODO : javadoc
 * 
 * @author Steve Ebersole
 */
public class MetadataExtracter {
    
    private final Helper helper;
    private final Environment environment;
    private final Class<?> antlrHierarchyClass;

    public MetadataExtracter(Environment environment, Helper helper) throws MojoExecutionException {
        this.environment = environment;
        this.helper = helper;
        this.antlrHierarchyClass = helper.getAntlrHierarchyClass();
    }

    public XRef processMetadata(org.codehaus.mojo.antlr.options.Grammar[] grammars) throws MojoExecutionException {
        Object hierarchy;
        Method readGrammarFileMethod;
        Method getFileMethod;
        try {
            Object antlrTool = helper.getAntlrToolClass().getDeclaredConstructor().newInstance();
            hierarchy = antlrHierarchyClass.getConstructor(new Class[] { helper.getAntlrToolClass() })
                                           .newInstance(new Object[] { antlrTool });

            readGrammarFileMethod = antlrHierarchyClass.getMethod("readGrammarFile", Helper.STRING_ARG_SIGNATURE);
            getFileMethod = antlrHierarchyClass.getMethod("getFile", Helper.STRING_ARG_SIGNATURE);
        } catch (Throwable t) {
            throw new MojoExecutionException("Unable to instantiate Antlr preprocessor tool", causeToUse(t));
        }

        List<GrammarFile> files = new ArrayList<>();
        for (int i = 0; i < grammars.length; i++) {
            String grammarName = grammars[i].getName().trim();
            if (isEmpty(grammarName)) {
                environment.getLog().info("Empty grammar in the configuration; skipping.");
                continue;
            }

            File grammar = new File(environment.getSourceDirectory(), grammarName);

            if (!grammar.exists()) {
                throw new MojoExecutionException("The grammar '" + grammar.getAbsolutePath() + "' doesnt exist.");
            }

            String grammarFilePath = grammar.getPath();
            GrammarFile grammarFile = new GrammarFile(
                    grammarName, 
                    grammarFilePath,
                    isNotEmpty(grammars[i].getGlib()) ? split(grammars[i].getGlib(), ":,") : new String[0]);

            // :( antlr.preprocessor.GrammarFile's only access to package is through a protected field :(
            try (BufferedReader in = new BufferedReader(new FileReader(grammar))) {
                
                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("package") && line.endsWith(";")) {
                        grammarFile.setPackageName(line.substring(8, line.length() - 1));
                        break;
                    }
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }

            files.add(grammarFile);

            try {
                readGrammarFileMethod.invoke(hierarchy, new Object[] { grammarFilePath });
            } catch (Throwable t) {
                throw new MojoExecutionException("Unable to use Antlr preprocessor to read grammar file", causeToUse(t));
            }
        }

        XRef xref = new XRef(hierarchy);
        for (GrammarFile gf : files) {
            String grammarFilePath = gf.getFileName();
            try {
                Object antlrGrammarFileDef = getFileMethod.invoke(hierarchy, new Object[] { grammarFilePath });
                intrepretMetadata(gf, antlrGrammarFileDef);
                xref.addGrammarFile(gf);
            } catch (Throwable t) {
                throw new MojoExecutionException("Unable to build grammar metadata", causeToUse(t));
            }
        }

        return xref;
    }

    private void intrepretMetadata(GrammarFile grammarFile, Object antlrGrammarFileDef) throws MojoExecutionException {
        try {
            Object grammarsVector = helper.getAntlrGrammarFileClass()
                                          .getMethod("getGrammars", NO_ARG_SIGNATURE)
                                          .invoke(antlrGrammarFileDef, NO_ARGS);

            @SuppressWarnings("unchecked")
            Enumeration<Object> grammars = (Enumeration<Object>) 
                                    helper.getAntlrIndexedVectorClass()
                                          .getMethod("elements", NO_ARG_SIGNATURE)
                                          .invoke(grammarsVector, NO_ARGS);
            
            while (grammars.hasMoreElements()) {
                Grammar grammar = new Grammar(grammarFile);
                intrepret(grammar, grammars.nextElement());
            }
        } catch (Throwable t) {
            throw new MojoExecutionException("Error attempting to access grammars within grammar file", t);
        }
    }

    private void intrepret(Grammar grammar, Object antlrGrammarDef) throws MojoExecutionException {
        try {
            Method getNameMethod = helper.getAntlrGrammarClass().getDeclaredMethod("getName", NO_ARG_SIGNATURE);
            getNameMethod.setAccessible(true);
            String name = (String) getNameMethod.invoke(antlrGrammarDef, NO_ARGS);
            grammar.setClassName(name);

            Method getSuperGrammarNameMethod = helper.getAntlrGrammarClass().getMethod("getSuperGrammarName", NO_ARG_SIGNATURE);
            getSuperGrammarNameMethod.setAccessible(true);
            String superGrammarName = (String) getSuperGrammarNameMethod.invoke(antlrGrammarDef, NO_ARGS);
            grammar.setSuperGrammarName(superGrammarName);

            Method getOptionsMethod = helper.getAntlrGrammarClass().getMethod("getOptions", NO_ARG_SIGNATURE);
            getOptionsMethod.setAccessible(true);
            Object options = getOptionsMethod.invoke(antlrGrammarDef, NO_ARGS);

            Method getElementMethod = helper.getAntlrIndexedVectorClass().getMethod("getElement", new Class[] { Object.class });
            getElementMethod.setAccessible(true);

            Method getRHSMethod = helper.getAntlrOptionClass().getMethod("getRHS", NO_ARG_SIGNATURE);
            getRHSMethod.setAccessible(true);

            Object importVocabOption = getElementMethod.invoke(options, new Object[] { "importVocab" });
            if (importVocabOption != null) {
                String importVocab = (String) getRHSMethod.invoke(importVocabOption, NO_ARGS);
                if (importVocab != null) {
                    importVocab = importVocab.trim();
                    if (importVocab.endsWith(";")) {
                        importVocab = importVocab.substring(0, importVocab.length() - 1);
                    }
                    grammar.setImportVocab(importVocab);
                }
            }

            Object exportVocabOption = getElementMethod.invoke(options, new Object[] { "exportVocab" });
            if (exportVocabOption != null) {
                String exportVocab = (String) getRHSMethod.invoke(exportVocabOption, NO_ARGS);
                if (exportVocab != null) {
                    exportVocab = exportVocab.trim();
                    if (exportVocab.endsWith(";")) {
                        exportVocab = exportVocab.substring(0, exportVocab.length() - 1);
                    }
                }
                grammar.setExportVocab(exportVocab);
            }
        } catch (Throwable t) {
            throw new MojoExecutionException("Error accessing  Antlr grammar metadata", t);
        }
    }

    private Throwable causeToUse(Throwable throwable) {
        if (throwable instanceof InvocationTargetException) {
            return ((InvocationTargetException) throwable).getTargetException();
        }
        
        return throwable;
    }
}
