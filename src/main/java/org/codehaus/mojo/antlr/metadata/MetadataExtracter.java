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

    public MetadataExtracter(final Environment environment, final Helper helper) throws MojoExecutionException {
        this.environment = environment;
        this.helper = helper;
        this.antlrHierarchyClass = helper.getAntlrHierarchyClass();
    }

    public XRef processMetadata(final org.codehaus.mojo.antlr.options.Grammar[] grammars) throws MojoExecutionException {
        final Object hierarchy;
        final Method readGrammarFileMethod;
        final Method getFileMethod;
        try {
            final Object antlrTool = helper.getAntlrToolClass().getDeclaredConstructor().newInstance();
            hierarchy = antlrHierarchyClass.getConstructor(new Class[] { helper.getAntlrToolClass() })
                                           .newInstance(antlrTool);

            readGrammarFileMethod = antlrHierarchyClass.getMethod("readGrammarFile", Helper.STRING_ARG_SIGNATURE);
            getFileMethod = antlrHierarchyClass.getMethod("getFile", Helper.STRING_ARG_SIGNATURE);
        } catch (final Throwable t) {
            throw new MojoExecutionException("Unable to instantiate Antlr preprocessor tool", causeToUse(t));
        }

        final List<GrammarFile> files = new ArrayList<>();
        for (final org.codehaus.mojo.antlr.options.Grammar value : grammars) {
            final String grammarName = value.getName().trim();
            if (isEmpty(grammarName)) {
                environment.getLog().info("Empty grammar in the configuration; skipping.");
                continue;
            }

            final File grammar = new File(environment.getSourceDirectory(), grammarName);

            if (!grammar.exists()) {
                throw new MojoExecutionException("The grammar '" + grammar.getAbsolutePath() + "' doesnt exist.");
            }

            final String grammarFilePath = grammar.getPath();
            final GrammarFile grammarFile = new GrammarFile(
                    grammarName,
                    grammarFilePath,
                    isNotEmpty(value.getGlib()) ? split(value.getGlib(), ":,") : new String[0]);

            // :( antlr.preprocessor.GrammarFile's only access to package is through a protected field :(
            try (final BufferedReader in = new BufferedReader(new FileReader(grammar))) {

                String line;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("package") && line.endsWith(";")) {
                        grammarFile.setPackageName(line.substring(8, line.length() - 1));
                        break;
                    }
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }

            files.add(grammarFile);

            try {
                readGrammarFileMethod.invoke(hierarchy, grammarFilePath);
            } catch (final Throwable t) {
                throw new MojoExecutionException("Unable to use Antlr preprocessor to read grammar file", causeToUse(t));
            }
        }

        final XRef xref = new XRef(hierarchy);
        for (final GrammarFile gf : files) {
            final String grammarFilePath = gf.getFileName();
            try {
                final Object antlrGrammarFileDef = getFileMethod.invoke(hierarchy, grammarFilePath);
                intrepretMetadata(gf, antlrGrammarFileDef);
                xref.addGrammarFile(gf);
            } catch (final Throwable t) {
                throw new MojoExecutionException("Unable to build grammar metadata", causeToUse(t));
            }
        }

        return xref;
    }

    private void intrepretMetadata(final GrammarFile grammarFile, final Object antlrGrammarFileDef) throws MojoExecutionException {
        try {
            final Object grammarsVector = helper.getAntlrGrammarFileClass()
                                          .getMethod("getGrammars", NO_ARG_SIGNATURE)
                                          .invoke(antlrGrammarFileDef, NO_ARGS);

            @SuppressWarnings("unchecked") final Enumeration<Object> grammars = (Enumeration<Object>)
                                    helper.getAntlrIndexedVectorClass()
                                          .getMethod("elements", NO_ARG_SIGNATURE)
                                          .invoke(grammarsVector, NO_ARGS);
            
            while (grammars.hasMoreElements()) {
                final Grammar grammar = new Grammar(grammarFile);
                intrepret(grammar, grammars.nextElement());
            }
        } catch (final Throwable t) {
            throw new MojoExecutionException("Error attempting to access grammars within grammar file", t);
        }
    }

    private void intrepret(final Grammar grammar, final Object antlrGrammarDef) throws MojoExecutionException {
        try {
            final Method getNameMethod = helper.getAntlrGrammarClass().getDeclaredMethod("getName", NO_ARG_SIGNATURE);
            getNameMethod.setAccessible(true);
            final String name = (String) getNameMethod.invoke(antlrGrammarDef, NO_ARGS);
            grammar.setClassName(name);

            final Method getSuperGrammarNameMethod = helper.getAntlrGrammarClass().getMethod("getSuperGrammarName", NO_ARG_SIGNATURE);
            getSuperGrammarNameMethod.setAccessible(true);
            final String superGrammarName = (String) getSuperGrammarNameMethod.invoke(antlrGrammarDef, NO_ARGS);
            grammar.setSuperGrammarName(superGrammarName);

            final Method getOptionsMethod = helper.getAntlrGrammarClass().getMethod("getOptions", NO_ARG_SIGNATURE);
            getOptionsMethod.setAccessible(true);
            final Object options = getOptionsMethod.invoke(antlrGrammarDef, NO_ARGS);

            final Method getElementMethod = helper.getAntlrIndexedVectorClass().getMethod("getElement", Object.class);
            getElementMethod.setAccessible(true);

            final Method getRHSMethod = helper.getAntlrOptionClass().getMethod("getRHS", NO_ARG_SIGNATURE);
            getRHSMethod.setAccessible(true);

            final Object importVocabOption = getElementMethod.invoke(options, "importVocab");
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

            final Object exportVocabOption = getElementMethod.invoke(options, "exportVocab");
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
        } catch (final Throwable t) {
            throw new MojoExecutionException("Error accessing  Antlr grammar metadata", t);
        }
    }

    private Throwable causeToUse(final Throwable throwable) {
        if (throwable instanceof InvocationTargetException) {
            return ((InvocationTargetException) throwable).getTargetException();
        }
        
        return throwable;
    }
}
