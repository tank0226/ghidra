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
package ghidra.app.plugin.core.codebrowser;

import org.apache.commons.lang3.StringUtils;

import ghidra.app.context.ListingActionContext;
import ghidra.program.util.ProgramLocation;

public class CodeViewerActionContext extends ListingActionContext {

	public CodeViewerActionContext(CodeViewerProvider provider) {
		super(provider, provider);
	}

	public CodeViewerActionContext(CodeViewerProvider provider, ProgramLocation location) {
		super(provider, provider, location);
	}

	@Override
	public boolean hasSelection() {
		CodeViewerProvider provider = (CodeViewerProvider) getComponentProvider();
		String textSelection = provider.getTextSelection();
		if (!StringUtils.isBlank(textSelection)) {
			return true;
		}

		return super.hasSelection();
	}
}
