"use client";

import React, { useState, useMemo } from "react";
import { KeyRound, ShieldAlert, CheckCircle2, User, Search, ShieldCheck, UserPlus } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/Table";
import { Card, CardContent } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { mockDb, type UserAccess } from "@/shared/mock/mockData";

export function IdentityAccessScreen() {
  const [members, setMembers] = useState<UserAccess[]>(mockDb.users);
  const [search, setSearch] = useState("");

  const filteredMembers = useMemo(() => {
    return members.filter(
      m =>
        m.name.toLowerCase().includes(search.toLowerCase()) ||
        m.email.toLowerCase().includes(search.toLowerCase()) ||
        m.role.toLowerCase().includes(search.toLowerCase())
    );
  }, [members, search]);

  const handleToggleStatus = (id: string) => {
    setMembers(prev =>
      prev.map(m => {
        if (m.id !== id) return m;
        const nextStatus = m.status === "active" ? "inactive" : "active";
        return { ...m, status: nextStatus };
      })
    );
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-xl font-bold text-slate-800">Identity & Access Control</h1>
          <p className="text-xs text-slate-400">Add sales agents, adjust security permission roles, and suspend user accounts</p>
        </div>
        <Button variant="primary" size="sm" className="gap-1.5 bg-blue-600 hover:bg-blue-700 text-xs font-semibold text-white">
          <UserPlus className="size-4" />
          <span>Invite Member</span>
        </Button>
      </div>

      <Card className="border-slate-100 shadow-sm bg-white">
        <CardContent className="py-3 px-4">
          <div className="relative w-full md:w-72">
            <Search className="absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder="Search staff name, email, role..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-8 pr-3 py-1.5 rounded-lg border border-slate-200 bg-slate-50 text-xs text-slate-800 focus:outline-none focus:border-blue-500 focus:bg-white transition"
            />
          </div>
        </CardContent>
      </Card>

      <div className="bg-white rounded-xl border border-slate-100 shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-slate-50 border-b border-slate-100 text-slate-500">
            <TableRow hoverable={false}>
              <TableHead className="font-semibold text-xs text-slate-500">Staff Member</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Email Address</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Role Title</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">Last Login</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500">System Status</TableHead>
              <TableHead className="font-semibold text-xs text-slate-500 text-center">Security Access Control</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredMembers.length > 0 ? (
              filteredMembers.map(member => (
                <TableRow key={member.id} className="hover:bg-slate-50/70 border-b border-slate-100 transition">
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">
                    <div className="flex items-center gap-2">
                      <span className="size-6 rounded-full bg-blue-100 text-blue-700 text-[10px] font-bold flex items-center justify-center">
                        {member.name.slice(0, 2).toUpperCase()}
                      </span>
                      {member.name}
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-xs text-slate-600 font-semibold">{member.email}</TableCell>
                  <TableCell className="py-3 px-4 text-xs font-bold text-slate-800">{member.role}</TableCell>
                  <TableCell className="py-3 px-4">
                    <div className="flex items-center gap-1.5 text-xs text-slate-600">
                      <span>{member.lastLogin}</span>
                    </div>
                  </TableCell>
                  <TableCell className="py-3 px-4">
                    <Badge variant={member.status === "active" ? "success" : "danger"} size="sm" className="font-bold text-[9px] uppercase">
                      {member.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="py-3 px-4 text-center">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleToggleStatus(member.id)}
                      className={`px-2.5 py-1 text-[10px] border-slate-200 font-bold transition ${
                        member.status === "active"
                          ? "text-red-600 hover:bg-red-50 hover:border-red-200"
                          : "text-blue-600 hover:bg-blue-50 hover:border-blue-200"
                      }`}
                    >
                      {member.status === "active" ? "Suspend Access" : "Activate Access"}
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            ) : (
              <TableRow hoverable={false}>
                <TableCell colSpan={6} className="py-8 text-center text-slate-400 text-xs">
                  No staff members matched your query.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
