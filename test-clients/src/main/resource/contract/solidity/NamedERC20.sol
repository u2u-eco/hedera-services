// contracts/NamedERC20.sol
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./token/ERC20/ERC20.sol";

contract NamedERC20 is ERC20 {
    constructor(
        string memory name_, 
        string memory symbol_,
        uint256 initialSupply
    ) ERC20(name_, symbol_) {
        _mint(msg.sender, initialSupply);
    }
}
