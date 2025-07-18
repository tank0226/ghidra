/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ghidra.app.plugin.core.debug.gui.modules;

import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.event.ListSelectionEvent;

import docking.widgets.table.TableColumnDescriptor;
import docking.widgets.table.TableFilter;
import ghidra.app.plugin.core.debug.gui.model.*;
import ghidra.app.plugin.core.debug.gui.model.ObjectTableModel.*;
import ghidra.app.plugin.core.debug.gui.model.columns.*;
import ghidra.docking.settings.Settings;
import ghidra.framework.plugintool.Plugin;
import ghidra.framework.plugintool.ServiceProvider;
import ghidra.program.model.address.Address;
import ghidra.program.model.address.AddressRange;
import ghidra.trace.model.Trace;
import ghidra.trace.model.modules.TraceModule;
import ghidra.trace.model.modules.TraceSection;
import ghidra.trace.model.target.TraceObject;
import ghidra.trace.model.target.TraceObjectValue;
import ghidra.trace.model.target.path.KeyPath;
import ghidra.trace.model.target.schema.TraceObjectSchema;
import ghidra.trace.model.thread.TraceProcess;

public class DebuggerSectionsPanel extends AbstractObjectsTableBasedPanel<TraceSection> {

	private static class SectionStartColumn extends AbstractTraceValueObjectAddressColumn {

		public SectionStartColumn() {
			super(TraceSection.KEY_RANGE);
		}

		@Override
		public String getColumnName() {
			return "Start";
		}

		@Override
		protected Address fromRange(AddressRange range) {
			return range.getMinAddress();
		}
	}

	private static class SectionEndColumn extends AbstractTraceValueObjectAddressColumn {
		public SectionEndColumn() {
			super(TraceSection.KEY_RANGE);
		}

		@Override
		public String getColumnName() {
			return "End";
		}

		@Override
		protected Address fromRange(AddressRange range) {
			return range.getMaxAddress();
		}
	}

	private static class SectionNameColumn extends TraceValueKeyColumn {
		@Override
		public String getColumnName() {
			return "Name";
		}

		@Override
		public String getValue(ValueRow rowObject, Settings settings, Trace data,
				ServiceProvider serviceProvider) throws IllegalArgumentException {
			return KeyPath.parseIfIndex(rowObject.getValue().getEntryKey());
		}
	}

	private static class SectionPathColumn extends TraceValueKeyColumn {
		@Override
		public String getColumnName() {
			return "Path";
		}

		@Override
		public String getValue(ValueRow rowObject, Settings settings, Trace data,
				ServiceProvider serviceProvider) throws IllegalArgumentException {
			return rowObject.getValue().getCanonicalPath().toString();
		}
	}

	private static class SectionModuleNameColumn extends TraceValueObjectPropertyColumn<String> {
		public SectionModuleNameColumn() {
			super(String.class);
		}

		@Override
		public String getColumnName() {
			return "Module Name";
		}

		@Override
		public ValueProperty<String> getProperty(ValueRow row) {
			return new ValueDerivedProperty<>(row, String.class) {
				@Override
				public String getValue() {
					TraceObject module = getModule(row);
					if (module == null) {
						return "";
					}
					TraceObjectValue nameEntry =
						module.getAttribute(row.currentSnap(), TraceModule.KEY_MODULE_NAME);
					if (nameEntry == null) {
						return "";
					}
					return nameEntry.getValue().toString();
				}
			};
		}
	}

	private static class SectionLengthColumn extends AbstractTraceValueObjectLengthColumn {
		public SectionLengthColumn() {
			super(TraceSection.KEY_RANGE);
		}

		@Override
		public String getColumnName() {
			return "Length";
		}
	}

	private class SectionTableModel extends ObjectTableModel {
		protected SectionTableModel(Plugin plugin) {
			super(plugin);
		}

		@Override
		protected TableColumnDescriptor<ValueRow> createTableColumnDescriptor() {
			TableColumnDescriptor<ValueRow> descriptor = new TableColumnDescriptor<>();
			descriptor.addHiddenColumn(new SectionPathColumn());
			descriptor.addVisibleColumn(new SectionStartColumn(), 1, true);
			descriptor.addVisibleColumn(new SectionEndColumn());
			descriptor.addVisibleColumn(new SectionNameColumn());
			descriptor.addVisibleColumn(new SectionModuleNameColumn());
			descriptor.addVisibleColumn(new SectionLengthColumn());
			return descriptor;
		}
	}

	private static TraceObject getModule(ValueRow row) {
		TraceObjectValue moduleEntry = row.getAttributeEntry(TraceSection.KEY_MODULE);
		if (moduleEntry != null && moduleEntry.isObject()) {
			return moduleEntry.getChild();
		}
		return row.getValue()
				.getChild()
				.findCanonicalAncestorsInterface(TraceModule.class)
				.findFirst()
				.orElse(null);
	}

	protected static ModelQuery successorSections(TraceObjectSchema rootSchema, KeyPath path) {
		TraceObjectSchema schema = rootSchema.getSuccessorSchema(path);
		return new ModelQuery(schema.searchFor(TraceSection.class, path, true));
	}

	private class SectionsBySelectedModulesTableFilter implements TableFilter<ValueRow> {
		@Override
		public boolean acceptsRow(ValueRow rowObject) {
			if (selectedModuleObjects.isEmpty()) {
				return true;
			}
			TraceObject module = getModule(rowObject);
			return selectedModuleObjects.contains(module);
		}

		@Override
		public boolean isSubFilterOf(TableFilter<?> tableFilter) {
			return false;
		}
	}

	private final DebuggerModulesProvider provider;
	private Set<Object> selectedModuleObjects = Set.of();

	private final SectionsBySelectedModulesTableFilter filterSectionsBySelectedModules =
		new SectionsBySelectedModulesTableFilter();

	public DebuggerSectionsPanel(DebuggerModulesProvider provider) {
		super(provider.plugin, provider, TraceSection.class);
		this.provider = provider;
	}

	@Override
	protected ObjectTableModel createModel() {
		return new SectionTableModel(plugin);
	}

	@Override
	protected ModelQuery computeQuery(TraceObject object) {
		TraceObjectSchema rootSchema = object.getRoot().getSchema();
		KeyPath seedPath = object.getCanonicalPath();
		KeyPath processPath = rootSchema.searchForAncestor(TraceProcess.class, seedPath);
		if (processPath != null) {
			ModelQuery result = successorSections(rootSchema, processPath);
			if (!result.isEmpty()) {
				return result;
			}
		}
		// Yes, anchor on the *module* container when searching for sections
		KeyPath containerPath = rootSchema.searchForSuitableContainer(TraceModule.class, seedPath);

		if (containerPath != null) {
			ModelQuery result = successorSections(rootSchema, containerPath);
			if (!result.isEmpty()) {
				return result;
			}
		}
		return successorSections(rootSchema, KeyPath.ROOT);
	}

	public void setFilteredBySelectedModules(boolean filtered) {
		if (filtered) {
			refreshSelectedModuleObjects();
		}
		filterPanel.setSecondaryFilter(filtered ? filterSectionsBySelectedModules : null);
	}

	public void setSelectedSections(Set<TraceSection> sel) {
		setSelected(sel);
	}

	@Override
	public void valueChanged(ListSelectionEvent e) {
		super.valueChanged(e);
		if (e.getValueIsAdjusting()) {
			return;
		}
		provider.sectionsPanelContextChanged();
	}

	private void refreshSelectedModuleObjects() {
		selectedModuleObjects = provider.modulesPanel.getSelectedItems()
				.stream()
				.map(r -> r.getValue().getValue())
				.collect(Collectors.toSet());
	}

	@Override
	public void reload() {
		refreshSelectedModuleObjects();
		super.reload();
	}
}
