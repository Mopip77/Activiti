/*
 * Copyright 2010-2020 Alfresco Software, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.engine.impl.persistence.entity;

import org.activiti.engine.api.internal.Internal;

/**


 */
@Internal
public interface Entity {

  String getId();

  void setId(String id);

  boolean isInserted();

  void setInserted(boolean inserted);

  boolean isUpdated();

  void setUpdated(boolean updated);

  boolean isDeleted();

  void setDeleted(boolean deleted);

  /**
   * CM：最终在cache写回db的时候会看这个的返回值，根据这个来判断是否需要写回数据库
   * Returns a representation of the object, as would be stored in the database.
   * Used when deciding if updates have occurred to the object or not since it was last loaded.
   */
  Object getPersistentState();
}
