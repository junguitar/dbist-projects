/**
 * Copyright 2011 the original author or authors.
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
package com.googlecode.dbist.util;

import java.util.Collection;
import java.util.Map;

public class Com {
	public static boolean isEmpty(Object value) {
		if (value == null)
			return true;
		if (value instanceof String)
			return ((String) value).isEmpty();
		else if (value instanceof Collection)
			return ((Collection<?>) value).isEmpty();
		else if (value instanceof Map)
			return ((Map<?, ?>) value).isEmpty();
		return false;
	}
}
