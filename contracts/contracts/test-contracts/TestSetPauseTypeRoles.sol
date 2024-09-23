// SPDX-License-Identifier: AGPL-3.0
pragma solidity >=0.8.19 <=0.8.24;

import { PauseManager } from "../messageService/lib/PauseManager.sol";

contract TestSetPauseTypeRoles is PauseManager {
  function initializePauseTypesAndPermissions(
    PauseTypeRole[] calldata _pauseTypeRoles,
    PauseTypeRole[] calldata _unpauseTypeRoles
  ) external initializer {
    __PauseManager_init(_pauseTypeRoles, _unpauseTypeRoles);
  }
}