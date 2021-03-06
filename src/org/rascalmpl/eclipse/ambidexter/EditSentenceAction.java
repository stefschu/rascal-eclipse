package org.rascalmpl.eclipse.ambidexter;

import org.eclipse.imp.pdb.facts.IValueFactory;
import org.rascalmpl.eclipse.library.util.ValueUI;
import org.rascalmpl.values.ValueFactoryFactory;

public class EditSentenceAction extends AbstractAmbidexterAction {
	public EditSentenceAction() {
		setText("Edit");
		setToolTipText("Open an editor for the selected sentence");
	}
	
	@Override
	public void run() {
		IValueFactory vf = ValueFactoryFactory.getValueFactory();
		new ValueUI(vf).text(vf.string(sentence), vf.integer(2));
	}
}
