/*******************************************************************************
 * Copyright (c) 2009-2015 CWI
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   * Jurgen J. Vinju - Jurgen.Vinju@cwi.nl - CWI
 *   * Tijs van der Storm - Tijs.van.der.Storm@cwi.nl
 *   * Emilie Balland - (CWI)
 *   * Anya Helene Bagge - A.H.S.Bagge@cwi.nl (Univ. Bergen)
 *   * Mark Hills - Mark.Hills@cwi.nl (CWI)
 *   * Arnold Lankamp - Arnold.Lankamp@cwi.nl
 *   * Michael Steindorfer - Michael.Steindorfer@cwi.nl - CWI
*******************************************************************************/
package org.rascalmpl.eclipse.editor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.imp.language.Language;
import org.eclipse.imp.language.LanguageRegistry;
import org.eclipse.imp.model.ISourceProject;
import org.eclipse.imp.parser.IMessageHandler;
import org.eclipse.imp.parser.IParseController;
import org.eclipse.imp.parser.ISourcePositionLocator;
import org.eclipse.imp.pdb.facts.IConstructor;
import org.eclipse.imp.pdb.facts.ISourceLocation;
import org.eclipse.imp.pdb.facts.exceptions.FactTypeUseException;
import org.eclipse.imp.services.IAnnotationTypeInfo;
import org.eclipse.imp.services.ILanguageSyntaxProperties;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.rascalmpl.eclipse.Activator;
import org.rascalmpl.eclipse.IRascalResources;
import org.rascalmpl.eclipse.ambidexter.ReportView;
import org.rascalmpl.eclipse.nature.IWarningHandler;
import org.rascalmpl.eclipse.nature.ProjectEvaluatorFactory;
import org.rascalmpl.eclipse.nature.RascalMonitor;
import org.rascalmpl.eclipse.nature.WarningsToMessageHandler;
import org.rascalmpl.eclipse.uri.ProjectURIResolver;
import org.rascalmpl.interpreter.Evaluator;
import org.rascalmpl.interpreter.asserts.Ambiguous;
import org.rascalmpl.interpreter.control_exceptions.Throw;
import org.rascalmpl.interpreter.staticErrors.StaticError;
import org.rascalmpl.interpreter.utils.Modules;
import org.rascalmpl.parser.gtd.exception.ParseError;
import org.rascalmpl.uri.FileURIResolver;
import org.rascalmpl.values.uptr.ITree;
import org.rascalmpl.values.uptr.TreeAdapter;

public class ParseController implements IParseController, IMessageHandlerProvider {
	private IMessageHandler handler;
	private ISourceProject project;
	private IConstructor parseTree;
	private IPath path;
	private Language language;
	private IDocument document;
	private ParseJob job;
	private Evaluator parser;
	private IWarningHandler warnings;
	
	public IAnnotationTypeInfo getAnnotationTypeInfo() {
		return null;
	}

	public IMessageHandler getMessageHandler() {
		return handler;
	}
	
	public Object getCurrentAst() {
		return parseTree;
	}
	
    public void setCurrentAst(IConstructor parseTree) {
		this.parseTree = parseTree;
	}

	public Language getLanguage() {
		if (language == null) {
			language = LanguageRegistry.findLanguage("Rascal");
		}
		return language;
	}

	public ISourcePositionLocator getSourcePositionLocator() {
		return new NodeLocator();
	}

	public IPath getPath() {
		return path;
	}

	public ISourceProject getProject() {
		return project;
	}

	public ILanguageSyntaxProperties getSyntaxProperties() {
		return new RascalSyntaxProperties();
	}
	
	public Iterator<Token> getTokenIterator(IRegion region) {
		return parseTree != null ? new TokenIterator(false, parseTree) : null;
	}
	
	@Override
	public void initialize(IPath filePath, ISourceProject project, IMessageHandler handler) {
		Assert.isTrue(filePath.isAbsolute() && project == null
				|| !filePath.isAbsolute() && project != null);
		
		this.path = filePath;
		this.handler = handler;
		this.project = project;

		ISourceLocation location = null;
		
		if (project != null) {
			location = ProjectURIResolver.constructProjectURI(project, path);
			this.parser = ProjectEvaluatorFactory.getInstance().getEvaluator(project.getRawProject(), new WarningsToMessageHandler(location, handler));
		} else {
			location = FileURIResolver.constructFileURI(path.toString());
			this.parser = ProjectEvaluatorFactory.getInstance().getEvaluator(null);
		}

		this.warnings = new WarningsToMessageHandler(location, handler);
		this.job = new ParseJob("Rascal parser", location, handler);
	}
	
	public IDocument getDocument() {
		return document;
	}
	
	public Object parse(IDocument doc, IProgressMonitor monitor) {
		if (doc == null) {
			return null;
		}
		this.document = doc;
		return parse(doc.get(), monitor);
	}
	
	private class ParseJob extends Job {
		private final ISourceLocation uri;
		private Set<IResource> markedFiles;

		private String input;
		public ITree parseTree = null;
		private String name = null;
		private final Set<String> ignore = new HashSet<>();

		public ParseJob(String name, ISourceLocation uri, IMessageHandler handler) {
			super(name);
			
			this.uri = uri;
		}
		
		public void initialize(String input) {
			this.input = input;
		}
		
		private void clearMarkers() {
      try {
        if (markedFiles != null) {
          for (IResource res : markedFiles) {
            res.deleteMarkers(IRascalResources.ID_RASCAL_MARKER, true, 0);
          }
          
          markedFiles = null;
        }
      } catch (CoreException e) {
        Activator.log("could not erase markers completely", e);
      }
    }
		
		@Override
		protected IStatus run(IProgressMonitor monitor) {
			RascalMonitor rm = new RascalMonitor(monitor, warnings);
			clearMarkers();
			rm.startJob("parsing", 500);
			parseTree = null;
			if (input == null || path == null || (path != null && !path.isAbsolute() && project == null)) {
				// may happen when project is deleted before Eclipse was started
				return null;
			}
			
			try {
				synchronized (parser) {
					if (project != null) {
						// if this is a source file in a Rascal project then
						// reload other modules to find out about new syntax definitions
						ProjectEvaluatorFactory.getInstance().reloadProject(project.getRawProject(), new WarningsToMessageHandler(uri, getMessageHandler()), ignore);
					}
					parseTree = parser.parseModule(rm, input.toCharArray(), uri);
					
					// this makes sure we do not reload the current module and who depends on it 
					// while we are editing it.
					name = Modules.getName(TreeAdapter.getStartTop(parseTree));
					ignore.clear();
					ignore.add(name);
				}
			}
			catch (FactTypeUseException ftue) {
				Activator.getInstance().logException("parsing rascal failed", ftue);
			}
			catch (ParseError pe){
				int offset = pe.getOffset();
				if(offset > 0 && offset == input.length()) {
					--offset;
				}
				int k = Math.min(offset + 20, input.length());
				String follow = input.substring(offset, k);
				StringBuffer msg = new StringBuffer();
				boolean hasUni = false;
				msg.append(pe.toString()).append(" FOLLOWED BY: ");
				for(int i = 0; i < follow.length();i ++){
					int c = follow.codePointAt(i);
					if((Character.isSpaceChar(c) &&
					   c != (int)' ' && c != (int)'\t' && c != (int)'\r' && c != (int)'\n')){
						
					   if(Character.charCount(c) == 1){
						   msg.append(String.format("\\u%04x", c));
					   } else {
						   msg.append(String.format("\\U%06x", c));
					   }
						hasUni = true;
					} else 
					   msg.appendCodePoint(c);
				}
				if(hasUni)
					msg.append(" NOTE: unrecognized characters occur at \\u followed by a hexadecimal number");
				setParseError(offset, pe.getLength(), pe.getBeginLine() + 1, pe.getBeginColumn(), pe.getEndLine() + 1, pe.getEndColumn(), msg.toString());
			}
			catch (StaticError e) {
				ISourceLocation loc = e.getLocation();
				
				if (loc.hasOffsetLength()) {
				  setParseError(loc.getOffset(), loc.getLength(), loc.getBeginLine(), loc.getBeginColumn(), loc.getEndLine(), loc.getEndColumn(), e.getMessage());
				}
				else {
				  Activator.log("weird error during parsing", e);
				}
			}
			catch (Throw t) {
				ISourceLocation loc = t.getLocation();
				
				setParseError(loc.getOffset(), loc.getLength(), loc.getBeginLine(), loc.getBeginColumn(), loc.getEndLine(), loc.getEndColumn(), t.getMessage());
			}
			catch (Ambiguous e) {
				ISourceLocation loc = e.getLocation();
				setParseError(loc.getOffset(), loc.getLength(), loc.getBeginLine(), loc.getBeginColumn(), loc.getEndLine(), loc.getEndColumn(), e.getMessage());
				// reparse with raw rascal parser to get the full forest
				ReportView.listAmbiguities(project.getName(), "editor", e.getTree(), new NullProgressMonitor());
				Activator.log("unexpected ambiguity during parsing of Rascal module", e);
			}
			finally {
				rm.endJob(true);
			}
			
			return Status.OK_STATUS;
		}
	}
	
	public Object parse(String input, IProgressMonitor monitor) {
		parseTree = null;
		try {
			job.initialize(input);
			job.schedule();
			job.join();
			parseTree = job.parseTree;
			return parseTree;
		} catch (InterruptedException e) {
			Activator.getInstance().logException("parser interrupted", e);
		}
		
		return null;
	}
	
	private void setParseError(int offset, int length, int beginLine, int beginColumn, int endLine, int endColumn, String message){
		if(offset >= 0){
			handler.handleSimpleMessage(message, offset, offset + ((length == 0) ? 0 : length - 1), beginColumn, endColumn, beginLine, endLine);
		}else{
			handler.handleSimpleMessage(message, 0, 0, 0, 0, 1, 1);
		}
	}
}
